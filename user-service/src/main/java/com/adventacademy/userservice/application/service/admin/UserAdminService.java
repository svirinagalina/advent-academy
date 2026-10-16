package com.adventacademy.userservice.application.service.admin;

import com.adventacademy.userservice.application.dto.UserAdminContract.*;
import com.adventacademy.userservice.application.port.EmailService;
import com.adventacademy.userservice.application.port.PasswordEncoder;
import com.adventacademy.userservice.application.service.auth.OpaqueTokens;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.*;
import java.util.*;

@Service
public class UserAdminService {
    public static final Set<String> ROLES =
            Set.of("ADMIN", "CONTENT_EDITOR", "INSTRUCTOR", "STUDENT");
    private final JdbcTemplate jdbc;
    private final UserAdminPolicy policy;
    private final PasswordEncoder passwords;
    private final EmailService emails;
    private final TransactionTemplate tx;
    private final Clock clock;

    private record Delivery(UUID userId, String email, String token, UUID generation, UUID actor) {}

    public UserAdminService(
            JdbcTemplate jdbc,
            UserAdminPolicy policy,
            PasswordEncoder passwords,
            EmailService emails,
            PlatformTransactionManager manager,
            Clock clock) {
        this.jdbc = jdbc;
        this.policy = policy;
        this.passwords = passwords;
        this.emails = emails;
        this.tx = new TransactionTemplate(manager);
        this.clock = clock;
    }

    private int update(String sql, Object... values) {
        Object[] args =
                Arrays.stream(values)
                        .map(
                                v ->
                                        v instanceof Instant instant
                                                ? java.sql.Timestamp.from(instant)
                                                : v)
                        .toArray();
        return jdbc.update(sql, args);
    }

    private Instant now() {
        return clock.instant();
    }

    private LocalDateTime localNow() {
        return LocalDateTime.ofInstant(now(), clock.getZone());
    }

    private Instant timestamp(ResultSet row, String name) throws SQLException {
        var value = row.getTimestamp(name);
        return value == null ? null : value.toLocalDateTime().atZone(clock.getZone()).toInstant();
    }

    private void managed(Set<String> roles) {
        if (roles == null || !ROLES.containsAll(roles))
            throw new AdminProblem(400, "INVALID_ROLES");
    }

    private void rowLock(UUID id) {
        if (jdbc.queryForList("SELECT id FROM users WHERE id=? FOR UPDATE", id).isEmpty())
            throw new AdminProblem(404, "USER_NOT_FOUND");
    }

    private List<String> roles(UUID id) {
        return jdbc.queryForList(
                "SELECT r.name FROM roles r JOIN user_roles ur ON ur.role_id=r.id WHERE"
                        + " ur.user_id=? ORDER BY r.name",
                String.class,
                id);
    }

    private String status(ResultSet r) throws SQLException {
        String status = r.getString("status");
        var until = timestamp(r, "locked_until");
        if (status.equals("LOCKED") && until != null && !until.isAfter(now())) return "ACTIVE";
        return status;
    }

    private User snapshot(UUID id) {
        var result =
                jdbc.query(
                        "SELECT * FROM users WHERE id=?",
                        (r, n) -> {
                            String status = status(r);
                            Instant until = timestamp(r, "locked_until");
                            boolean temporary =
                                    until != null && until.isAfter(now())
                                            || status.equals("LOCKED") && until == null;
                            boolean last = policy.isLastAdmin(id);
                            return new User(
                                    id,
                                    r.getString("first_name"),
                                    r.getString("last_name"),
                                    r.getString("email"),
                                    roles(id),
                                    status,
                                    !Set.of("PENDING", "DELETED").contains(status),
                                    r.getBoolean("email_verified"),
                                    temporary,
                                    until,
                                    timestamp(r, "created_at"),
                                    r.getLong("admin_version"),
                                    invitation(id),
                                    !last,
                                    Set.of("ACTIVE", "LOCKED").contains(status) && !last,
                                    status.equals("BLOCKED"),
                                    temporary);
                        },
                        id);
        if (result.isEmpty()) throw new AdminProblem(404, "USER_NOT_FOUND");
        return result.getFirst();
    }

    private Invitation invitation(UUID id) {
        var result =
                jdbc.query(
                        "SELECT * FROM user_invitations WHERE user_id=?",
                        (r, n) -> {
                            String state = r.getString("state");
                            Instant expires = r.getTimestamp("expires_at").toInstant();
                            Instant retry =
                                    r.getTimestamp("attempted_at").toInstant().plusSeconds(60);
                            if (state.equals("SENT") && !expires.isAfter(now())) state = "EXPIRED";
                            if (state.equals("SENDING") && !retry.isAfter(now())) state = "UNKNOWN";
                            var sent = r.getTimestamp("sent_at");
                            return new Invitation(
                                    state, expires, sent == null ? null : sent.toInstant(), retry);
                        },
                        id);
        return result.isEmpty() ? null : result.getFirst();
    }

    public User get(UUID id) {
        policy.requireAdmin();
        return tx.execute(s -> snapshot(id));
    }

    public Page<User> list(String query, String role, String status, int page, int size) {
        policy.requireAdmin();
        if (query.length() > 255
                || page < 0
                || page > 100000
                || size < 1
                || size > 100
                || !role.isEmpty() && !ROLES.contains(role)
                || !status.isEmpty()
                        && !Set.of("ACTIVE", "PENDING", "LOCKED", "BLOCKED", "SUSPENDED")
                                .contains(status)) throw new AdminProblem(400, "INVALID_FILTER");
        String pattern =
                "%"
                        + query.trim()
                                .toLowerCase(Locale.ROOT)
                                .replace("!", "!!")
                                .replace("%", "!%")
                                .replace("_", "!_")
                        + "%";
        String where =
                """
WHERE u.status<>'DELETED' AND (lower(u.first_name||' '||u.last_name) LIKE ? ESCAPE '!'
OR lower(u.email) LIKE ? ESCAPE '!') AND (?='' OR EXISTS(SELECT 1 FROM user_roles ur
JOIN roles r ON r.id=ur.role_id WHERE ur.user_id=u.id AND r.name=?))
AND (?='' OR (CASE WHEN u.status='LOCKED' AND u.locked_until<=? THEN 'ACTIVE' ELSE u.status END)=?)
""";
        Object[] args = {pattern, pattern, role, role, status, localNow(), status};
        return tx.execute(
                s -> {
                    long total =
                            Objects.requireNonNull(
                                    jdbc.queryForObject(
                                            "SELECT count(*) FROM users u " + where,
                                            Long.class,
                                            args));
                    var parameters = new ArrayList<>(Arrays.asList(args));
                    parameters.add(size);
                    parameters.add((long) page * size);
                    var ids =
                            jdbc.queryForList(
                                    "SELECT u.id FROM users u "
                                            + where
                                            + " ORDER BY u.created_at DESC,u.id DESC LIMIT ? OFFSET"
                                            + " ?",
                                    UUID.class,
                                    parameters.toArray());
                    return new Page<>(ids.stream().map(this::snapshot).toList(), total, page, size);
                });
    }

    public User create(Create input) {
        managed(input.roles());
        String email = input.email().trim().toLowerCase(Locale.ROOT);
        String first = input.firstName().trim(), last = input.lastName().trim();
        String fingerprint =
                OpaqueTokens.digest(
                        email
                                + "\n"
                                + first
                                + "\n"
                                + last
                                + "\n"
                                + String.join(",", new TreeSet<>(input.roles())));
        UUID[] id = new UUID[1];
        Delivery delivery =
                tx.execute(
                        s -> {
                            policy.lock();
                            UUID actor = policy.requireAdmin();
                            var previous =
                                    jdbc.queryForList(
                                            "SELECT user_id,fingerprint FROM admin_user_creations"
                                                    + " WHERE actor_id=? AND request_id=?",
                                            actor,
                                            input.requestId());
                            if (!previous.isEmpty()) {
                                if (!fingerprint.equals(previous.getFirst().get("fingerprint")))
                                    throw new AdminProblem(409, "REQUEST_REUSED");
                                id[0] = (UUID) previous.getFirst().get("user_id");
                                return null;
                            }
                            var duplicate =
                                    jdbc.queryForList(
                                            "SELECT id FROM users WHERE lower(btrim(email))=?",
                                            UUID.class,
                                            email);
                            if (!duplicate.isEmpty())
                                throw new AdminProblem(409, "EMAIL_EXISTS", duplicate.getFirst());
                            id[0] = UUID.randomUUID();
                            update(
                                    """
INSERT INTO users(id,email,password_hash,first_name,last_name,status,email_verified,mfa_enabled,
failed_login_attempts,created_at,updated_at) VALUES(?,?,?,?,?,'PENDING',false,false,0,?,?)
""",
                                    id[0],
                                    email,
                                    passwords.encode(OpaqueTokens.newToken()),
                                    first,
                                    last,
                                    localNow(),
                                    localNow());
                            for (String role : input.roles())
                                update(
                                        "INSERT INTO user_roles(user_id,role_id) SELECT ?,id FROM"
                                                + " roles WHERE name=?",
                                        id[0],
                                        role);
                            update(
                                    "INSERT INTO admin_user_creations VALUES(?,?,?,?)",
                                    actor,
                                    input.requestId(),
                                    id[0],
                                    fingerprint);
                            event(
                                    actor,
                                    id[0],
                                    "USER_CREATED",
                                    "",
                                    String.join(",", new TreeSet<>(input.roles())));
                            return prepare(id[0], email, input.requestId(), actor);
                        });
        if (delivery != null) deliver(delivery);
        return tx.execute(s -> snapshot(id[0]));
    }

    private Delivery prepare(UUID id, String email, UUID request, UUID actor) {
        String token = OpaqueTokens.newToken();
        UUID generation = UUID.randomUUID();
        update("INSERT INTO user_invitation_requests VALUES(?,?)", id, request);
        update(
                """
INSERT INTO user_invitations(user_id,generation,request_id,token_digest,state,expires_at,attempted_at)
VALUES(?,?,?,?,'SENDING',?,?) ON CONFLICT(user_id) DO UPDATE SET generation=EXCLUDED.generation,
request_id=EXCLUDED.request_id,token_digest=EXCLUDED.token_digest,state='SENDING',expires_at=EXCLUDED.expires_at,
attempted_at=EXCLUDED.attempted_at,sent_at=null,accepted_at=null
""",
                id,
                generation,
                request,
                OpaqueTokens.digest(token),
                now().plus(Duration.ofHours(24)),
                now());
        return new Delivery(id, email, token, generation, actor);
    }

    private void deliver(Delivery delivery) {
        boolean sent;
        try {
            emails.sendInvitation(delivery.email(), delivery.token());
            sent = true;
        } catch (RuntimeException e) {
            sent = false;
        }
        final boolean success = sent;
        tx.executeWithoutResult(
                s -> {
                    int changed =
                            update(
                                    "UPDATE user_invitations SET state=?,sent_at=? WHERE user_id=?"
                                            + " AND generation=? AND state='SENDING'",
                                    success ? "SENT" : "FAILED",
                                    success ? now() : null,
                                    delivery.userId(),
                                    delivery.generation());
                    if (changed > 0)
                        event(
                                delivery.actor(),
                                delivery.userId(),
                                success ? "INVITATION_SENT" : "INVITATION_FAILED",
                                "",
                                "");
                });
    }

    public User resend(UUID id, UUID request) {
        Delivery delivery =
                tx.execute(
                        s -> {
                            policy.lock();
                            UUID actor = policy.requireAdmin();
                            rowLock(id);
                            User user = snapshot(id);
                            if (user.status().equals("DELETED"))
                                throw new AdminProblem(409, "USER_DISABLED");
                            if (user.activated() || user.emailVerified())
                                throw new AdminProblem(409, "ALREADY_ACTIVATED");
                            var previous =
                                    jdbc.queryForList(
                                            "SELECT request_id FROM user_invitations WHERE"
                                                    + " user_id=?",
                                            id);
                            if (previous.isEmpty()) throw new AdminProblem(409, "NOT_INVITED");
                            if (!jdbc.queryForList(
                                            "SELECT request_id FROM user_invitation_requests WHERE"
                                                    + " user_id=? AND request_id=?",
                                            id,
                                            request)
                                    .isEmpty()) return null;
                            if (user.invitation().canResendAt().isAfter(now()))
                                throw new AdminProblem(429, "INVITATION_COOLDOWN");
                            return prepare(id, user.email(), request, actor);
                        });
        if (delivery != null) deliver(delivery);
        return tx.execute(s -> snapshot(id));
    }

    public User changeRoles(UUID id, Roles input) {
        managed(input.roles());
        return tx.execute(
                s -> {
                    policy.lock();
                    UUID actor = policy.requireAdmin();
                    rowLock(id);
                    User user = snapshot(id);
                    if (user.version() != input.version())
                        throw new AdminProblem(409, "VERSION_CONFLICT");
                    if (user.status().equals("DELETED"))
                        throw new AdminProblem(409, "USER_DISABLED");
                    if (user.roles().contains("ADMIN") && !input.roles().contains("ADMIN"))
                        policy.protect(id);
                    var before = new TreeSet<>(user.roles());
                    for (String role : ROLES) {
                        if (!input.roles().contains(role))
                            update(
                                    "DELETE FROM user_roles WHERE user_id=? AND role_id IN(SELECT"
                                            + " id FROM roles WHERE name=?)",
                                    id,
                                    role);
                        else
                            update(
                                    "INSERT INTO user_roles(user_id,role_id) SELECT ?,id FROM roles"
                                            + " WHERE name=? ON CONFLICT DO NOTHING",
                                    id,
                                    role);
                    }
                    User changed = snapshot(id);
                    if (!before.equals(new TreeSet<>(changed.roles())))
                        event(
                                actor,
                                id,
                                "ROLES_CHANGED",
                                String.join(",", before),
                                String.join(",", changed.roles()));
                    return changed;
                });
    }

    public User access(UUID id, String action, long version) {
        return tx.execute(
                s -> {
                    policy.lock();
                    UUID actor = policy.requireAdmin();
                    rowLock(id);
                    User user = snapshot(id);
                    if (user.version() != version) throw new AdminProblem(409, "VERSION_CONFLICT");
                    switch (action) {
                        case "block" -> {
                            if (!Set.of("ACTIVE", "LOCKED").contains(user.status()))
                                throw new AdminProblem(409, "INVALID_TRANSITION");
                            policy.protect(id);
                            update(
                                    "UPDATE users SET status='BLOCKED',updated_at=? WHERE id=?",
                                    localNow(),
                                    id);
                        }
                        case "unblock" -> {
                            if (!user.status().equals("BLOCKED"))
                                throw new AdminProblem(409, "INVALID_TRANSITION");
                            update(
                                    "UPDATE users SET status=?,updated_at=? WHERE id=?",
                                    !user.emailVerified()
                                            ? "PENDING"
                                            : user.temporarilyLocked() ? "LOCKED" : "ACTIVE",
                                    localNow(),
                                    id);
                        }
                        case "unlock" -> {
                            if (!user.temporarilyLocked())
                                throw new AdminProblem(409, "INVALID_TRANSITION");
                            update(
                                    "UPDATE users SET status=CASE WHEN status='LOCKED' THEN"
                                        + " 'ACTIVE' ELSE status"
                                        + " END,locked_until=null,failed_login_attempts=0,updated_at=?"
                                        + " WHERE id=?",
                                    localNow(),
                                    id);
                        }
                        default -> throw new AdminProblem(400, "INVALID_ACTION");
                    }
                    User changed = snapshot(id);
                    event(
                            actor,
                            id,
                            action.toUpperCase(Locale.ROOT),
                            user.status(),
                            changed.status());
                    return changed;
                });
    }

    public void accept(Accept input) {
        if (!input.password().equals(input.confirmPassword()))
            throw new AdminProblem(400, "PASSWORD_MISMATCH");
        if (input.password().getBytes(StandardCharsets.UTF_8).length > 72)
            throw new AdminProblem(400, "PASSWORD_TOO_LONG");
        String hash = passwords.encode(input.password());
        tx.executeWithoutResult(
                s -> {
                    policy.lock();
                    var ids =
                            jdbc.queryForList(
                                    "SELECT user_id FROM user_invitations WHERE token_digest=?",
                                    UUID.class,
                                    OpaqueTokens.digest(input.token()));
                    if (ids.isEmpty()) throw new AdminProblem(400, "INVALID_INVITATION");
                    UUID id = ids.getFirst();
                    rowLock(id);
                    User user = snapshot(id);
                    var current =
                            jdbc.queryForList(
                                    "SELECT * FROM user_invitations WHERE user_id=? AND"
                                            + " token_digest=? FOR UPDATE",
                                    id,
                                    OpaqueTokens.digest(input.token()));
                    if (current.isEmpty()
                            || !user.status().equals("PENDING")
                            || user.emailVerified()
                            || "ACCEPTED".equals(current.getFirst().get("state"))
                            || !((java.sql.Timestamp) current.getFirst().get("expires_at"))
                                    .toInstant()
                                    .isAfter(now()))
                        throw new AdminProblem(400, "INVALID_INVITATION");
                    update(
                            "UPDATE users SET"
                                + " password_hash=?,email_verified=true,status='ACTIVE',failed_login_attempts=0,locked_until=null,updated_at=?"
                                + " WHERE id=?",
                            hash,
                            localNow(),
                            id);
                    update(
                            "UPDATE user_invitations SET state='ACCEPTED',accepted_at=? WHERE"
                                    + " user_id=?",
                            now(),
                            id);
                    event(id, id, "INVITATION_ACCEPTED", "PENDING", "ACTIVE");
                });
    }

    private void event(UUID actor, UUID user, String action, String before, String after) {
        String name =
                jdbc.queryForObject(
                        "SELECT first_name||' '||last_name FROM users WHERE id=?",
                        String.class,
                        actor);
        update(
                "INSERT INTO user_admin_events VALUES(?,?,?,?,?,?,?,?)",
                UUID.randomUUID(),
                actor,
                name,
                user,
                action,
                before,
                after,
                now());
    }

    public Page<Event> history(UUID id, int page, int size) {
        policy.requireAdmin();
        if (page < 0 || page > 100000 || size < 1 || size > 100)
            throw new AdminProblem(400, "INVALID_FILTER");
        return tx.execute(
                s -> {
                    snapshot(id);
                    var items =
                            jdbc.query(
                                    "SELECT * FROM user_admin_events WHERE user_id=? ORDER BY"
                                            + " created_at DESC,id DESC LIMIT ? OFFSET ?",
                                    (r, n) ->
                                            new Event(
                                                    r.getObject("id", UUID.class),
                                                    id,
                                                    r.getString("actor_name"),
                                                    r.getString("action"),
                                                    r.getString("before_value"),
                                                    r.getString("after_value"),
                                                    r.getTimestamp("created_at").toInstant()),
                                    id,
                                    size,
                                    (long) page * size);
                    return new Page<>(
                            items,
                            Objects.requireNonNull(
                                    jdbc.queryForObject(
                                            "SELECT count(*) FROM user_admin_events WHERE"
                                                    + " user_id=?",
                                            Long.class,
                                            id)),
                            page,
                            size);
                });
    }
}
