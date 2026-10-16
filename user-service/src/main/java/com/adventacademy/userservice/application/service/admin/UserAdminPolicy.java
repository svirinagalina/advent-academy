package com.adventacademy.userservice.application.service.admin;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class UserAdminPolicy {
    private final JdbcTemplate jdbc;

    public UserAdminPolicy(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // Shared by every administrative removal path, including the legacy role/delete APIs.
    public void lock() {
        jdbc.queryForList("SELECT pg_advisory_xact_lock(20465401)");
    }

    public UUID requireAdmin() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) throw new AdminProblem(403, "ADMIN_REQUIRED");
        UUID id;
        try {
            id = UUID.fromString(auth.getName());
        } catch (IllegalArgumentException e) {
            throw new AdminProblem(403, "ADMIN_REQUIRED");
        }
        if (!isActiveAdmin(id)) throw new AdminProblem(403, "ADMIN_REQUIRED");
        return id;
    }

    public boolean isActiveAdmin(UUID id) {
        return Boolean.TRUE.equals(
                jdbc.queryForObject(
                        """
                        SELECT EXISTS(SELECT 1 FROM users u JOIN user_roles ur ON ur.user_id=u.id
                        JOIN roles r ON r.id=ur.role_id WHERE u.id=? AND r.name='ADMIN'
                        AND u.status IN ('ACTIVE','LOCKED') AND u.email_verified=true)
                        """,
                        Boolean.class,
                        id));
    }

    public boolean isLastAdmin(UUID id) {
        // A temporary login lock does not remove the administrator's enabled account.
        return isActiveAdmin(id)
                && jdbc.queryForObject(
                                """
SELECT count(*) FROM users u WHERE u.status IN ('ACTIVE','LOCKED') AND u.email_verified=true
AND EXISTS(SELECT 1 FROM user_roles ur JOIN roles r ON r.id=ur.role_id
WHERE ur.user_id=u.id AND r.name='ADMIN')
""",
                                Long.class)
                        == 1;
    }

    public void recordRoleChange(
            UUID user, java.util.Collection<String> before, java.util.Collection<String> after) {
        if (new java.util.TreeSet<>(before).equals(new java.util.TreeSet<>(after))) return;
        UUID actor = actorOr(null);
        String name =
                actor == null
                        ? "Системное действие"
                        : jdbc.queryForObject(
                                "SELECT first_name||' '||last_name FROM users WHERE id=?",
                                String.class,
                                actor);
        jdbc.update(
                "INSERT INTO user_admin_events VALUES(?,?,?,?,?,?,?,now())",
                UUID.randomUUID(),
                actor,
                name,
                user,
                "ROLES_CHANGED",
                String.join(",", new java.util.TreeSet<>(before)),
                String.join(",", new java.util.TreeSet<>(after)));
    }

    public UUID actorOr(UUID fallback) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return fallback;
        try {
            return UUID.fromString(auth.getName());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    public void authorizeRequestIfPresent() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) requireAdmin();
    }

    public void protect(UUID id) {
        if (isLastAdmin(id)) throw new AdminProblem(409, "LAST_ADMIN");
    }
}
