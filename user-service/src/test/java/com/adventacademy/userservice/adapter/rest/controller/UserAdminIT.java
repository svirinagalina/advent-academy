package com.adventacademy.userservice.adapter.rest.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import com.adventacademy.userservice.application.port.EmailService;
import com.adventacademy.userservice.application.port.JwtProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(
        properties = {
            "spring.flyway.enabled=true",
            "spring.jpa.hibernate.ddl-auto=validate",
            "security.rate-limit.enabled=false"
        })
class UserAdminIT {
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        r.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtProvider jwt;
    @MockitoBean EmailService mail;
    UUID admin;
    String auth;

    @Test
    void deletingAPendingAccountPreventsFurtherInvitations() throws Exception {
        String id =
                call("POST", "/api/users/admin", createInput(UUID.randomUUID()), auth)
                        .body()
                        .path("id")
                        .asText();
        assertThat(call("DELETE", "/api/users/" + id, null, auth).status()).isEqualTo(204);
        var response =
                call(
                        "POST",
                        "/api/users/admin/" + id + "/invitation",
                        Map.of("requestId", UUID.randomUUID()),
                        auth);
        assertThat(response.status()).isEqualTo(409);
        assertThat(response.body().path("code").asText()).isEqualTo("USER_DISABLED");
        verify(mail, times(1)).sendInvitation(anyString(), anyString());
    }

    @BeforeEach
    void prepare() {
        jdbc.update("DELETE FROM user_invitation_requests");
        jdbc.update("DELETE FROM user_admin_events");
        jdbc.update("DELETE FROM admin_user_creations");
        jdbc.update("DELETE FROM user_invitations");
        jdbc.update("DELETE FROM user_roles");
        jdbc.update("DELETE FROM users");
        reset(mail);
        admin = account("administrator@example.test", "ADMIN");
        auth = bearer(admin, "ADMIN");
    }

    UUID account(String email, String role) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO"
                    + " users(id,email,password_hash,first_name,last_name,status,email_verified,mfa_enabled,failed_login_attempts,created_at,updated_at)"
                    + " VALUES(?,?,'unused','Елена','Морозова','ACTIVE',true,false,0,now(),now())",
                id,
                email);
        jdbc.update("INSERT INTO user_roles SELECT ?,id FROM roles WHERE name=?", id, role);
        return id;
    }

    String bearer(UUID id, String role) {
        return "Bearer " + jwt.generateAccessToken(id, "test@example.test", Set.of(role));
    }

    record Reply(int status, JsonNode body) {}

    Reply call(String method, String path, Object body, String token) throws Exception {
        var request =
                switch (method) {
                    case "GET" -> get(path);
                    case "PUT" -> put(path);
                    case "DELETE" -> delete(path);
                    default -> post(path);
                };
        if (token != null) request.header("Authorization", token);
        if (body != null)
            request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body));
        var response = mvc.perform(request).andReturn().getResponse();
        return new Reply(
                response.getStatus(),
                response.getContentAsString().isEmpty()
                        ? json.nullNode()
                        : json.readTree(response.getContentAsString()));
    }

    Map<String, Object> createInput(UUID request) {
        return Map.of(
                "requestId",
                request,
                "email",
                "invited@example.test",
                "firstName",
                "Анна",
                "lastName",
                "Волкова",
                "roles",
                List.of("STUDENT", "INSTRUCTOR"));
    }

    @Test
    void adminOnlyAndFreshDatabaseAuthorityEvenForStaleJwt() throws Exception {
        assertThat(call("GET", "/api/users/admin", null, null).status()).isEqualTo(401);
        for (String role : List.of("STUDENT", "INSTRUCTOR", "CONTENT_EDITOR")) {
            UUID id = account(role.toLowerCase() + "@example.test", role);
            assertThat(call("GET", "/api/users/admin", null, bearer(id, role)).status())
                    .isEqualTo(403);
            assertThat(
                            call(
                                            "POST",
                                            "/api/users/admin",
                                            createInput(UUID.randomUUID()),
                                            bearer(id, role))
                                    .status())
                    .isEqualTo(403);
        }
        jdbc.update("DELETE FROM user_roles WHERE user_id=?", admin);
        assertThat(call("GET", "/api/users/admin", null, auth).status()).isEqualTo(403);
    }

    @Test
    void creationReplayPartialDeliveryAndOneTimeActivation() throws Exception {
        AtomicReference<String> token = new AtomicReference<>();
        doAnswer(
                        i -> {
                            token.set(i.getArgument(1));
                            throw new EmailService.SendEmailException("synthetic failure");
                        })
                .when(mail)
                .sendInvitation(anyString(), anyString());
        UUID request = UUID.randomUUID();
        var input = createInput(request);
        Reply result = call("POST", "/api/users/admin", input, auth);
        assertThat(result.status()).isEqualTo(200);
        String id = result.body().path("id").asText();
        assertThat(result.body().path("invitation").path("status").asText()).isEqualTo("FAILED");
        assertThat(result.body().toString())
                .doesNotContain(token.get(), "passwordHash", "tokenDigest");
        assertThat(call("POST", "/api/users/admin", input, auth).body().path("id").asText())
                .isEqualTo(id);
        verify(mail, times(1)).sendInvitation(anyString(), anyString());
        var changed = new HashMap<>(input);
        changed.put("firstName", "Другая");
        assertThat(call("POST", "/api/users/admin", changed, auth).body().path("code").asText())
                .isEqualTo("REQUEST_REUSED");
        jdbc.update(
                "UPDATE user_invitations SET attempted_at=now()-interval '2 minutes' WHERE"
                        + " user_id=?",
                UUID.fromString(id));
        String oldToken = token.get();
        doAnswer(
                        i -> {
                            token.set(i.getArgument(1));
                            return null;
                        })
                .when(mail)
                .sendInvitation(anyString(), anyString());
        assertThat(
                        call(
                                        "POST",
                                        "/api/users/admin/" + id + "/invitation",
                                        Map.of("requestId", UUID.randomUUID()),
                                        auth)
                                .body()
                                .path("invitation")
                                .path("status")
                                .asText())
                .isEqualTo("SENT");
        assertThat(
                        call(
                                        "POST",
                                        "/api/auth/invitations/accept",
                                        Map.of(
                                                "token",
                                                oldToken,
                                                "password",
                                                "Strong-password-1!",
                                                "confirmPassword",
                                                "Strong-password-1!"),
                                        null)
                                .status())
                .isEqualTo(400);
        var accept =
                Map.of(
                        "token",
                        token.get(),
                        "password",
                        "Strong-password-1!",
                        "confirmPassword",
                        "Strong-password-1!");
        assertThat(call("POST", "/api/auth/invitations/accept", accept, null).status())
                .isEqualTo(200);
        assertThat(call("POST", "/api/auth/invitations/accept", accept, null).status())
                .isEqualTo(400);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT email_verified AND status='ACTIVE' FROM users WHERE id=?",
                                Boolean.class,
                                UUID.fromString(id)))
                .isTrue();
        assertThat(
                        call(
                                        "POST",
                                        "/api/users/admin/" + id + "/invitation",
                                        Map.of("requestId", UUID.randomUUID()),
                                        auth)
                                .status())
                .isEqualTo(409);
        assertThat(
                        call("GET", "/api/users/admin/" + id + "/history", null, auth)
                                .body()
                                .path("items")
                                .get(0)
                                .path("actorName")
                                .asText())
                .isEqualTo("Анна Волкова");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM users WHERE email='invited@example.test'",
                                Long.class))
                .isEqualTo(1);
    }

    @Test
    void rolesConflictLastAdminAndLegacyBypassProtection() throws Exception {
        var me = call("GET", "/api/users/admin/" + admin, null, auth).body();
        long version = me.path("version").asLong();
        assertThat(me.path("canRemoveAdmin").asBoolean()).isFalse();
        assertThat(
                        call(
                                        "PUT",
                                        "/api/users/admin/" + admin + "/roles",
                                        Map.of("version", version, "roles", List.of("STUDENT")),
                                        auth)
                                .body()
                                .path("code")
                                .asText())
                .isEqualTo("LAST_ADMIN");
        String adminRole =
                jdbc.queryForObject("SELECT id FROM roles WHERE name='ADMIN'", UUID.class)
                        .toString();
        assertThat(
                        call(
                                        "DELETE",
                                        "/api/roles/users/" + admin + "/roles/" + adminRole,
                                        null,
                                        auth)
                                .status())
                .isEqualTo(409);
        assertThat(call("DELETE", "/api/users/" + admin, null, auth).status()).isEqualTo(409);
        UUID other = account("other@example.test", "STUDENT");
        long current =
                call("GET", "/api/users/admin/" + other, null, auth)
                        .body()
                        .path("version")
                        .asLong();
        assertThat(
                        call(
                                        "PUT",
                                        "/api/users/admin/" + other + "/roles",
                                        Map.of("version", current, "roles", List.of("INSTRUCTOR")),
                                        auth)
                                .status())
                .isEqualTo(200);
        assertThat(
                        call(
                                        "PUT",
                                        "/api/users/admin/" + other + "/roles",
                                        Map.of("version", current, "roles", List.of("ADMIN")),
                                        auth)
                                .body()
                                .path("code")
                                .asText())
                .isEqualTo("VERSION_CONFLICT");
        assertThat(
                        call(
                                        "GET",
                                        "/api/users/admin?query=OTHER&role=INSTRUCTOR&status=ACTIVE",
                                        null,
                                        auth)
                                .body()
                                .path("total")
                                .asInt())
                .isEqualTo(1);
    }

    @Test
    void concurrentAdminRemovalLeavesAnEnabledAdministrator() throws Exception {
        UUID second = account("second@example.test", "ADMIN");
        long firstVersion =
                call("GET", "/api/users/admin/" + admin, null, auth)
                        .body()
                        .path("version")
                        .asLong();
        long secondVersion =
                call("GET", "/api/users/admin/" + second, null, auth)
                        .body()
                        .path("version")
                        .asLong();
        var pool = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            var first =
                    pool.submit(
                            () -> {
                                start.await();
                                return call(
                                                "PUT",
                                                "/api/users/admin/" + admin + "/roles",
                                                Map.of("version", firstVersion, "roles", List.of()),
                                                auth)
                                        .status();
                            });
            var last =
                    pool.submit(
                            () -> {
                                start.await();
                                return call(
                                                "PUT",
                                                "/api/users/admin/" + second + "/roles",
                                                Map.of(
                                                        "version",
                                                        secondVersion,
                                                        "roles",
                                                        List.of()),
                                                bearer(second, "ADMIN"))
                                        .status();
                            });
            start.countDown();
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), last.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT count(*) FROM user_roles ur JOIN roles r ON"
                                            + " r.id=ur.role_id WHERE r.name='ADMIN'",
                                    Long.class))
                    .isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void temporaryAndAdministrativeLocksAreIndependent() throws Exception {
        UUID other = account("locked@example.test", "STUDENT");
        jdbc.update(
                "UPDATE users SET status='LOCKED',locked_until=now()+interval '10 minutes' WHERE"
                        + " id=?",
                other);
        var user = call("GET", "/api/users/admin/" + other, null, auth).body();
        var blocked =
                call(
                                "POST",
                                "/api/users/admin/" + other + "/block",
                                Map.of("version", user.path("version").asLong()),
                                auth)
                        .body();
        assertThat(blocked.path("temporarilyLocked").asBoolean()).isTrue();
        var unlocked =
                call(
                                "POST",
                                "/api/users/admin/" + other + "/unlock",
                                Map.of("version", blocked.path("version").asLong()),
                                auth)
                        .body();
        assertThat(unlocked.path("status").asText()).isEqualTo("BLOCKED");
        assertThat(unlocked.path("temporarilyLocked").asBoolean()).isFalse();
        assertThat(
                        call(
                                        "POST",
                                        "/api/users/admin/" + other + "/unblock",
                                        Map.of("version", unlocked.path("version").asLong()),
                                        auth)
                                .body()
                                .path("status")
                                .asText())
                .isEqualTo("ACTIVE");
    }

    @Test
    void expiredInvitationRequiresRenewalAndConcurrentAcceptanceSucceedsOnce() throws Exception {
        AtomicReference<String> token = new AtomicReference<>();
        doAnswer(
                        i -> {
                            token.set(i.getArgument(1));
                            return null;
                        })
                .when(mail)
                .sendInvitation(anyString(), anyString());
        var created = call("POST", "/api/users/admin", createInput(UUID.randomUUID()), auth).body();
        UUID id = UUID.fromString(created.path("id").asText());
        jdbc.update(
                "UPDATE user_invitations SET expires_at=now()-interval '1"
                        + " minute',attempted_at=now()-interval '2 minutes' WHERE user_id=?",
                id);
        assertThat(
                        call("GET", "/api/users/admin/" + id, null, auth)
                                .body()
                                .path("invitation")
                                .path("status")
                                .asText())
                .isEqualTo("EXPIRED");
        assertThat(
                        call(
                                        "POST",
                                        "/api/auth/invitations/accept",
                                        Map.of(
                                                "token",
                                                token.get(),
                                                "password",
                                                "Strong-password-1!",
                                                "confirmPassword",
                                                "Strong-password-1!"),
                                        null)
                                .status())
                .isEqualTo(400);
        assertThat(
                        call(
                                        "POST",
                                        "/api/users/admin/" + id + "/invitation",
                                        Map.of("requestId", UUID.randomUUID()),
                                        auth)
                                .status())
                .isEqualTo(200);
        var accept =
                Map.of(
                        "token",
                        token.get(),
                        "password",
                        "Strong-password-1!",
                        "confirmPassword",
                        "Strong-password-1!");
        var pool = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            var first =
                    pool.submit(
                            () -> {
                                start.await();
                                return call("POST", "/api/auth/invitations/accept", accept, null)
                                        .status();
                            });
            var second =
                    pool.submit(
                            () -> {
                                start.await();
                                return call("POST", "/api/auth/invitations/accept", accept, null)
                                        .status();
                            });
            start.countDown();
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 400);
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT count(*) FROM user_admin_events WHERE user_id=? AND"
                                            + " action='INVITATION_ACCEPTED'",
                                    Long.class,
                                    id))
                    .isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void anOldResendReplayNeverInvalidatesANewerInvitation() throws Exception {
        AtomicReference<String> token = new AtomicReference<>();
        doAnswer(
                        i -> {
                            token.set(i.getArgument(1));
                            return null;
                        })
                .when(mail)
                .sendInvitation(anyString(), anyString());
        UUID id =
                UUID.fromString(
                        call("POST", "/api/users/admin", createInput(UUID.randomUUID()), auth)
                                .body()
                                .path("id")
                                .asText());
        UUID oldRequest = UUID.randomUUID();
        for (UUID request : List.of(oldRequest, UUID.randomUUID())) {
            jdbc.update(
                    "UPDATE user_invitations SET attempted_at=now()-interval '2 minutes' WHERE"
                            + " user_id=?",
                    id);
            assertThat(
                            call(
                                            "POST",
                                            "/api/users/admin/" + id + "/invitation",
                                            Map.of("requestId", request),
                                            auth)
                                    .status())
                    .isEqualTo(200);
        }
        String latest = token.get();
        jdbc.update(
                "UPDATE user_invitations SET attempted_at=now()-interval '2 minutes' WHERE"
                        + " user_id=?",
                id);
        assertThat(
                        call(
                                        "POST",
                                        "/api/users/admin/" + id + "/invitation",
                                        Map.of("requestId", oldRequest),
                                        auth)
                                .status())
                .isEqualTo(200);
        verify(mail, times(3)).sendInvitation(anyString(), anyString());
        assertThat(
                        call(
                                        "POST",
                                        "/api/auth/invitations/accept",
                                        Map.of(
                                                "token",
                                                latest,
                                                "password",
                                                "Strong-password-1!",
                                                "confirmPassword",
                                                "Strong-password-1!"),
                                        null)
                                .status())
                .isEqualTo(200);
    }

    @Test
    void failedLoginsCannotReplaceAnAdministrativeBlockWithATemporaryLock() throws Exception {
        UUID other = account("blocked@example.test", "STUDENT");
        jdbc.update("UPDATE users SET status='BLOCKED' WHERE id=?", other);
        for (int i = 0; i < 6; i++) {
            assertThat(
                            call(
                                            "POST",
                                            "/api/auth/login",
                                            Map.of(
                                                    "email",
                                                    "blocked@example.test",
                                                    "password",
                                                    "Incorrect-password-1!"),
                                            null)
                                    .status())
                    .isEqualTo(401);
        }
        assertThat(jdbc.queryForObject("SELECT status FROM users WHERE id=?", String.class, other))
                .isEqualTo("BLOCKED");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT locked_until IS NULL FROM users WHERE id=?",
                                Boolean.class,
                                other))
                .isTrue();
    }

    @Test
    void explicitVersionIsRequiredAndLegacyChangesInvalidateOpenForms() throws Exception {
        UUID other = account("legacy@example.test", "STUDENT");
        long version =
                call("GET", "/api/users/admin/" + other, null, auth)
                        .body()
                        .path("version")
                        .asLong();
        assertThat(
                        call(
                                        "PUT",
                                        "/api/users/admin/" + other + "/roles",
                                        Map.of("roles", List.of("INSTRUCTOR")),
                                        auth)
                                .status())
                .isEqualTo(400);
        UUID role = jdbc.queryForObject("SELECT id FROM roles WHERE name='INSTRUCTOR'", UUID.class);
        assertThat(
                        call("POST", "/api/roles/users/" + other + "/roles/" + role, null, auth)
                                .status())
                .isEqualTo(200);
        assertThat(
                        call(
                                        "PUT",
                                        "/api/users/admin/" + other + "/roles",
                                        Map.of("version", version, "roles", List.of("ADMIN")),
                                        auth)
                                .body()
                                .path("code")
                                .asText())
                .isEqualTo("VERSION_CONFLICT");
        var history =
                call("GET", "/api/users/admin/" + other + "/history", null, auth)
                        .body()
                        .path("items");
        assertThat(history.get(0).path("actorName").asText()).isEqualTo("Елена Морозова");
        jdbc.update("UPDATE users SET first_name='Переименована' WHERE id=?", admin);
        assertThat(
                        call("GET", "/api/users/admin/" + other + "/history", null, auth)
                                .body()
                                .path("items")
                                .get(0)
                                .path("actorName")
                                .asText())
                .isEqualTo("Елена Морозова");
    }
}
