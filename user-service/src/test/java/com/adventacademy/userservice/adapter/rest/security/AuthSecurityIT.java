package com.adventacademy.userservice.adapter.rest.security;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.adventacademy.userservice.application.dto.request.CompletePasswordResetRequest;
import com.adventacademy.userservice.application.service.auth.OpaqueTokens;
import com.adventacademy.userservice.application.service.auth.PasswordResetService;
import com.adventacademy.userservice.application.service.auth.VerificationTokenService;
import com.adventacademy.userservice.domain.exception.InvalidTokenException;
import com.adventacademy.userservice.domain.model.AuditEventType;
import com.adventacademy.userservice.domain.model.VerificationToken;
import com.adventacademy.userservice.infrastructure.email.MockEmailServiceAdapter;
import com.adventacademy.userservice.infrastructure.persistence.entity.AuditEventEntity;
import com.adventacademy.userservice.infrastructure.persistence.entity.UserEntity;
import com.adventacademy.userservice.infrastructure.persistence.entity.VerificationTokenEntity;
import com.adventacademy.userservice.infrastructure.persistence.jpa.AuditEventJpaRepository;
import com.adventacademy.userservice.infrastructure.persistence.jpa.RecoveryCodeJpaRepository;
import com.adventacademy.userservice.infrastructure.persistence.jpa.UserJpaRepository;
import com.adventacademy.userservice.infrastructure.persistence.jpa.VerificationTokenJpaRepository;
import com.adventacademy.userservice.infrastructure.security.TotpProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security integration tests for issue #45: MFA, e-mail verification, password reset, rate limits.
 * <p>
 * Runs against a real PostgreSQL (Testcontainers) with the Flyway migrations applied, so the
 * V9-V11 schema is exercised together with the entities. Runs without a test-level transaction on
 * purpose: e-mails leave after commit, the rate limiter counts in its own transactions and the
 * concurrency test needs real commits.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
class AuthSecurityIT {

    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    private static final Pattern TOKEN_IN_MAIL = Pattern.compile("token: (\\S+)");
    private static final String PASSWORD = "Str0ng-Password!";
    private static final String NEW_PASSWORD = "An0ther-Password!";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserJpaRepository users;
    @Autowired private VerificationTokenJpaRepository tokens;
    @Autowired private RecoveryCodeJpaRepository recoveryCodes;
    @Autowired private AuditEventJpaRepository auditEvents;
    @Autowired private VerificationTokenService tokenService;
    @Autowired private PasswordResetService passwordResetService;
    @Autowired private TotpProvider totp;

    private final List<String> secretsSeen = new ArrayList<>();
    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void cleanSlate() {
        jdbc.update("DELETE FROM recovery_codes");
        jdbc.update("DELETE FROM verification_tokens");
        jdbc.update("DELETE FROM auth_rate_limit_buckets");
        jdbc.update("DELETE FROM audit_events");
        jdbc.update("DELETE FROM user_roles");
        jdbc.update("DELETE FROM users");
        MockEmailServiceAdapter.clearSentEmails();
        secretsSeen.clear();
        logs = new ListAppender<>();
        logs.start();
        ((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).addAppender(logs);
    }

    @AfterEach
    void detachLogs() {
        ((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).detachAppender(logs);
    }

    // ---------------------------------------------------------------- e-mail verification

    @Test
    void registerConfirmLogin_endToEnd_withDigestOnlyOneTimeToken() throws Exception {
        String email = "new@example.com";
        String token = register(email);

        UserEntity user = users.findByEmail(email).orElseThrow();
        assertThat(user.getEmailVerificationToken()).as("legacy plaintext column stays empty").isNull();
        assertThat(user.isEmailVerified()).isFalse();
        VerificationTokenEntity stored = tokens.findByTokenDigest(OpaqueTokens.digest(token)).orElseThrow();
        assertThat(stored.getPurpose()).isEqualTo(VerificationToken.PURPOSE_EMAIL_VERIFICATION);
        assertThat(stored.getTokenDigest()).isNotEqualTo(token);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM verification_tokens WHERE token_digest = ?", Integer.class, token)).isZero();

        mvc.perform(post("/api/auth/confirm-email").param("token", token))
                .andExpect(status().isOk());
        assertThat(users.findByEmail(email).orElseThrow().isEmailVerified()).isTrue();

        // one-time
        mvc.perform(post("/api/auth/confirm-email").param("token", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN"));

        // malformed / unknown
        mvc.perform(post("/api/auth/confirm-email").param("token", "definitely-not-a-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN"));

        login(email, PASSWORD, null).andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").isString());
        assertThat(auditTypes()).contains("USER_REGISTERED", "EMAIL_CONFIRMED", "LOGIN_SUCCESS");
    }

    @Test
    void expiredVerificationTokenIsRefusedAndAudited() throws Exception {
        String token = register("late@example.com");
        jdbc.update("UPDATE verification_tokens SET expires_at = ? WHERE token_digest = ?",
                java.time.OffsetDateTime.now().minusDays(1), OpaqueTokens.digest(token));

        mvc.perform(post("/api/auth/confirm-email").param("token", token))
                .andExpect(status().isBadRequest());

        assertThat(auditEvents.findAll()).anySatisfy(event -> {
            assertThat(event.getEventType()).isEqualTo("EMAIL_CONFIRMED");
            assertThat(event.getReason()).isEqualTo("token expired");
        });
    }

    @Test
    void resendVerification_issuesAFreshTokenAndRetiresTheOldOne() throws Exception {
        String email = "again@example.com";
        String first = register(email);

        mvc.perform(postJson("/api/auth/resend-verification", Map.of("email", email)))
                .andExpect(status().isOk());
        String second = lastTokenMailedTo(email);

        assertThat(second).isNotEqualTo(first);
        mvc.perform(post("/api/auth/confirm-email").param("token", first)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/confirm-email").param("token", second)).andExpect(status().isOk());
    }

    // ---------------------------------------------------------------- password reset

    @Test
    void passwordReset_endToEnd_changesPasswordAndRevokesRefreshSessions() throws Exception {
        String email = "reset@example.com";
        registerAndConfirm(email);
        String refreshToken = readJson(login(email, PASSWORD, null).andReturn()).get("refreshToken").asText();

        mvc.perform(postJson("/api/auth/password-reset", Map.of("email", email))).andExpect(status().isOk());
        String resetToken = lastTokenMailedTo(email);
        assertThat(tokens.findByTokenDigest(OpaqueTokens.digest(resetToken)).orElseThrow().getPurpose())
                .isEqualTo(VerificationToken.PURPOSE_PASSWORD_RESET);

        mvc.perform(postJson("/api/auth/password-reset/confirm",
                        Map.of("token", resetToken, "newPassword", NEW_PASSWORD, "confirmPassword", NEW_PASSWORD)))
                .andExpect(status().isOk());

        login(email, PASSWORD, null).andExpect(status().isUnauthorized());
        login(email, NEW_PASSWORD, null).andExpect(status().isOk());
        mvc.perform(postJson("/api/auth/refresh", Map.of("refreshToken", refreshToken)))
                .andExpect(status().is4xxClientError());

        // the token is gone after one use
        mvc.perform(postJson("/api/auth/password-reset/confirm",
                        Map.of("token", resetToken, "newPassword", "Yet-An0ther-1", "confirmPassword", "Yet-An0ther-1")))
                .andExpect(status().isBadRequest());
        login(email, NEW_PASSWORD, null).andExpect(status().isOk());

        assertThat(auditTypes()).contains("PASSWORD_RESET_INITIATED", "PASSWORD_RESET_COMPLETED", "SESSIONS_REVOKED");
    }

    @Test
    void changePassword_revokesTheOtherRefreshSessions() throws Exception {
        String email = "change@example.com";
        registerAndConfirm(email);
        JsonNode first = readJson(login(email, PASSWORD, null).andReturn());
        JsonNode second = readJson(login(email, PASSWORD, null).andReturn());

        mvc.perform(postJson("/api/auth/change-password", Map.of("oldPassword", PASSWORD, "newPassword", NEW_PASSWORD))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + first.get("accessToken").asText()))
                .andExpect(status().isOk());

        mvc.perform(postJson("/api/auth/refresh", Map.of("refreshToken", second.get("refreshToken").asText())))
                .andExpect(status().is4xxClientError());
        mvc.perform(postJson("/api/auth/refresh", Map.of("refreshToken", first.get("refreshToken").asText())))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void emailVerificationTokenCannotBeUsedAsPasswordResetToken() throws Exception {
        String email = "purpose@example.com";
        String verificationToken = register(email);

        mvc.perform(postJson("/api/auth/password-reset/confirm",
                        Map.of("token", verificationToken, "newPassword", NEW_PASSWORD, "confirmPassword", NEW_PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN"));

        // and the verification token is still intact for its real purpose
        mvc.perform(post("/api/auth/confirm-email").param("token", verificationToken)).andExpect(status().isOk());
        assertThat(auditEvents.findAll()).anySatisfy(event ->
                assertThat(event.getReason()).isEqualTo("token wrong_purpose"));
    }

    @Test
    void concurrentUseOfOneResetToken_succeedsExactlyOnce() throws Exception {
        String email = "race@example.com";
        registerAndConfirm(email);
        UUID userId = users.findByEmail(email).orElseThrow().getId();
        String token = tokenService.issue(userId, VerificationToken.PURPOSE_PASSWORD_RESET);

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger refused = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit((Callable<Void>) () -> {
                start.await();
                try {
                    passwordResetService.complete(new CompletePasswordResetRequest(token, NEW_PASSWORD, NEW_PASSWORD, null, null));
                    successes.incrementAndGet();
                } catch (InvalidTokenException e) {
                    refused.incrementAndGet();
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : futures) {
            f.get();
        }
        pool.shutdown();

        assertThat(successes.get()).isEqualTo(1);
        assertThat(refused.get()).isEqualTo(threads - 1);
    }

    // ---------------------------------------------------------------- account enumeration

    @Test
    void publicEndpointsAnswerIdenticallyForKnownAndUnknownAccounts() throws Exception {
        String known = "known@example.com";
        registerAndConfirm(known);
        String unknown = "nobody@example.com";

        assertSameResponse(postJson("/api/auth/password-reset", Map.of("email", known)),
                postJson("/api/auth/password-reset", Map.of("email", unknown)));
        assertSameResponse(postJson("/api/auth/resend-verification", Map.of("email", known)),
                postJson("/api/auth/resend-verification", Map.of("email", unknown)));
        assertSameResponse(loginRequest(known, "wrong-password", null), loginRequest(unknown, "wrong-password", null));
        // only the known, unverified-or-existing account actually got mail for the reset
        assertThat(MockEmailServiceAdapter.getEmailsTo(unknown)).isEmpty();
    }

    // ---------------------------------------------------------------- rate limiting

    @Test
    void passwordReset_isLimitedPerAccountAcrossAddressesAndPerAddress() throws Exception {
        // test profile: password-reset-per-account 3, password-reset-per-ip 5
        for (int i = 0; i < 3; i++) {
            mvc.perform(from(postJson("/api/auth/password-reset", Map.of("email", "victim@example.com")), "10.0.0." + i))
                    .andExpect(status().isOk());
        }
        mvc.perform(from(postJson("/api/auth/password-reset", Map.of("email", "victim@example.com")), "10.0.0.99"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.message").isString());
        // case/whitespace variants are the same account
        mvc.perform(from(postJson("/api/auth/password-reset", Map.of("email", " Victim@Example.com ")), "10.0.0.98"))
                .andExpect(status().isTooManyRequests());

        // per-IP: one address, five different accounts fit, the sixth does not
        for (int i = 0; i < 5; i++) {
            mvc.perform(from(postJson("/api/auth/password-reset", Map.of("email", "other" + i + "@example.com")), "10.1.1.1"))
                    .andExpect(status().isOk());
        }
        mvc.perform(from(postJson("/api/auth/password-reset", Map.of("email", "other9@example.com")), "10.1.1.1"))
                .andExpect(status().isTooManyRequests());
        // ...while another address is unaffected
        mvc.perform(from(postJson("/api/auth/password-reset", Map.of("email", "other9@example.com")), "10.1.1.2"))
                .andExpect(status().isOk());
    }

    @Test
    void login_isLimitedPerAccountAndForgedForwardedHeadersDoNotResetTheAddressQuota() throws Exception {
        // login-per-account 10 (account lock kicks in at 5 failed attempts, so use bogus accounts
        // to exercise the address quota: login-per-ip 20)
        for (int i = 0; i < 20; i++) {
            mvc.perform(from(loginRequest("ghost" + i + "@example.com", "x", null), "10.2.2.2")
                            .header("X-Forwarded-For", "203.0.113." + i)
                            .header("X-Real-IP", "198.51.100." + i))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(from(loginRequest("ghost99@example.com", "x", null), "10.2.2.2")
                        .header("X-Forwarded-For", "203.0.113.250"))
                .andExpect(status().isTooManyRequests());

        // per account: the same address is fine for another account until that account's quota is used up
        for (int i = 0; i < 10; i++) {
            mvc.perform(from(loginRequest("target@example.com", "x", null), "10.3.3." + i))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(from(loginRequest("target@example.com", "x", null), "10.3.3.200"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void oversizedAuthBodyIsRejectedBeforeParsing() throws Exception {
        String huge = "{\"email\":\"a@b.c\",\"password\":\"" + "x".repeat(20_000) + "\"}";
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(huge))
                .andExpect(status().isPayloadTooLarge());
    }

    // ---------------------------------------------------------------- MFA

    @Test
    void mfa_enrolLoginRecoverDisable_endToEnd() throws Exception {
        String email = "mfa@example.com";
        registerAndConfirm(email);
        String access = readJson(login(email, PASSWORD, null).andReturn()).get("accessToken").asText();

        // step-up: password required
        mvc.perform(postJson("/api/auth/mfa/enable", Map.of("password", "wrong")).header(HttpHeaders.AUTHORIZATION, "Bearer " + access))
                .andExpect(status().isUnauthorized());
        mvc.perform(postJson("/api/auth/mfa/enable", Map.of()).header(HttpHeaders.AUTHORIZATION, "Bearer " + access))
                .andExpect(status().isBadRequest());

        JsonNode enrolment = readJson(mvc.perform(postJson("/api/auth/mfa/enable", Map.of("password", PASSWORD))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + access))
                .andExpect(status().isOk()).andReturn());
        String secret = enrolment.get("secret").asText();
        secretsSeen.add(secret);
        assertThat(enrolment.get("qrCodeUrl").asText()).startsWith("otpauth://totp/");
        assertThat(enrolment.get("qrCodeImage").asText()).startsWith("data:image/png;base64,");

        // encrypted at rest, not enabled yet
        String storedSecret = jdbc.queryForObject("SELECT mfa_secret FROM users WHERE email = ?", String.class, email);
        assertThat(storedSecret).startsWith("v1:").doesNotContain(secret);
        assertThat(users.findByEmail(email).orElseThrow().isMfaEnabled()).isFalse();
        login(email, PASSWORD, null).andExpect(status().isOk()).andExpect(jsonPath("$.mfaRequired").value(false));

        // confirm with the authenticator code -> recovery codes, shown once
        String code = currentCode(secret);
        mvc.perform(postJson("/api/auth/mfa/confirm", Map.of("code", "000000")).header(HttpHeaders.AUTHORIZATION, "Bearer " + access))
                .andExpect(status().isUnauthorized());
        JsonNode confirmed = readJson(mvc.perform(postJson("/api/auth/mfa/confirm", Map.of("code", code))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + access))
                .andExpect(status().isOk()).andReturn());
        List<String> recovery = new ArrayList<>();
        confirmed.get("recoveryCodes").forEach(node -> recovery.add(node.asText()));
        secretsSeen.addAll(recovery);
        assertThat(recovery).hasSize(8);
        UUID userId = users.findByEmail(email).orElseThrow().getId();
        assertThat(recoveryCodes.countUnusedByUserId(userId)).isEqualTo(8);
        assertThat(jdbc.queryForList("SELECT code_hash FROM recovery_codes", String.class)).doesNotContainAnyElementsOf(recovery);

        // login now needs the second factor; the code used for enrolment is a replay
        login(email, PASSWORD, null).andExpect(status().isOk()).andExpect(jsonPath("$.mfaRequired").value(true));
        login(email, PASSWORD, code).andExpect(status().isUnauthorized());
        login(email, PASSWORD, "123456").andExpect(status().isUnauthorized());

        // recovery code works once
        login(email, PASSWORD, recovery.get(0)).andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").isString());
        login(email, PASSWORD, recovery.get(0)).andExpect(status().isUnauthorized());
        assertThat(recoveryCodes.countUnusedByUserId(userId)).isEqualTo(7);
        assertThat(auditTypes()).contains("MFA_ENROLLMENT_STARTED", "MFA_ENABLED", "MFA_RECOVERY_CODE_USED");

        // disable: password + second factor (another recovery code); everything is wiped
        String fresh = readJson(login(email, PASSWORD, recovery.get(1)).andReturn()).get("accessToken").asText();
        mvc.perform(postJson("/api/auth/mfa/disable", Map.of("password", PASSWORD, "code", recovery.get(1)))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + fresh))
                .andExpect(status().isUnauthorized()); // already burnt
        mvc.perform(postJson("/api/auth/mfa/disable", Map.of("password", "wrong", "code", recovery.get(2)))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + fresh))
                .andExpect(status().isUnauthorized());
        assertThat(recoveryCodes.countUnusedByUserId(userId)).as("a failed step-up burns nothing").isEqualTo(6);
        mvc.perform(postJson("/api/auth/mfa/disable", Map.of("password", PASSWORD, "code", recovery.get(2)))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + fresh))
                .andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT mfa_secret FROM users WHERE email = ?", String.class, email)).isNull();
        assertThat(recoveryCodes.countUnusedByUserId(userId)).isZero();
        login(email, PASSWORD, null).andExpect(status().isOk()).andExpect(jsonPath("$.mfaRequired").value(false));
        assertThat(auditTypes()).contains("MFA_DISABLED");

        assertNothingSecretLeaked();
    }

    @Test
    void mfaEndpointsRequireAnAccessToken() throws Exception {
        mvc.perform(postJson("/api/auth/mfa/enable", Map.of("password", PASSWORD))).andExpect(status().isUnauthorized());
        mvc.perform(postJson("/api/auth/mfa/confirm", Map.of("code", "123456"))).andExpect(status().isUnauthorized());
        mvc.perform(postJson("/api/auth/mfa/disable", Map.of("password", PASSWORD, "code", "123456"))).andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------- redaction

    @Test
    void tokensNeverAppearInLogsAuditOrErrorResponses() throws Exception {
        String email = "quiet@example.com";
        String verification = register(email);
        secretsSeen.add(verification);
        mvc.perform(post("/api/auth/confirm-email").param("token", verification)).andExpect(status().isOk());
        mvc.perform(postJson("/api/auth/password-reset", Map.of("email", email))).andExpect(status().isOk());
        String reset = lastTokenMailedTo(email);
        secretsSeen.add(reset);
        MvcResult refused = mvc.perform(postJson("/api/auth/password-reset/confirm",
                        Map.of("token", reset, "newPassword", NEW_PASSWORD, "confirmPassword", NEW_PASSWORD)))
                .andExpect(status().isOk()).andReturn();
        MvcResult reused = mvc.perform(postJson("/api/auth/password-reset/confirm",
                        Map.of("token", reset, "newPassword", NEW_PASSWORD, "confirmPassword", NEW_PASSWORD)))
                .andExpect(status().isBadRequest()).andReturn();

        assertThat(refused.getResponse().getContentAsString()).doesNotContain(reset);
        assertThat(reused.getResponse().getContentAsString()).doesNotContain(reset);
        assertNothingSecretLeaked();
    }

    // ---------------------------------------------------------------- helpers

    private String register(String email) throws Exception {
        mvc.perform(postJson("/api/auth/register", Map.of(
                        "email", email, "password", PASSWORD, "firstName", "Test", "lastName", "User")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.emailVerified").value(false));
        return lastTokenMailedTo(email);
    }

    private void registerAndConfirm(String email) throws Exception {
        String token = register(email);
        mvc.perform(post("/api/auth/confirm-email").param("token", token)).andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions login(String email, String password, String mfaCode) throws Exception {
        return mvc.perform(loginRequest(email, password, mfaCode));
    }

    private MockHttpServletRequestBuilder loginRequest(String email, String password, String mfaCode) throws Exception {
        Map<String, String> body = mfaCode == null
                ? Map.of("email", email, "password", password)
                : Map.of("email", email, "password", password, "mfaCode", mfaCode);
        return postJson("/api/auth/login", body);
    }

    private MockHttpServletRequestBuilder postJson(String path, Map<String, ?> body) throws Exception {
        return post(path).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
    }

    private static MockHttpServletRequestBuilder from(MockHttpServletRequestBuilder request, String remoteAddr) {
        return request.with(r -> {
            r.setRemoteAddr(remoteAddr);
            return r;
        });
    }

    private String lastTokenMailedTo(String email) {
        List<MockEmailServiceAdapter.MockEmail> mails = MockEmailServiceAdapter.getEmailsTo(email);
        assertThat(mails).as("mail to " + email).isNotEmpty();
        Matcher matcher = TOKEN_IN_MAIL.matcher(mails.get(mails.size() - 1).body());
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private String currentCode(String secret) {
        return totp.codeForStep(secret, Math.floorDiv(Instant.now().getEpochSecond(), TotpProvider.TIME_STEP_SECONDS));
    }

    private JsonNode readJson(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    private List<String> auditTypes() {
        return auditEvents.findAll().stream().map(AuditEventEntity::getEventType).toList();
    }

    /** Same status and same body apart from the response timestamp. */
    private void assertSameResponse(MockHttpServletRequestBuilder a, MockHttpServletRequestBuilder b) throws Exception {
        MvcResult ra = mvc.perform(a).andReturn();
        MvcResult rb = mvc.perform(b).andReturn();
        assertThat(ra.getResponse().getStatus()).isEqualTo(rb.getResponse().getStatus());
        assertThat(withoutTimestamp(ra)).isEqualTo(withoutTimestamp(rb));
    }

    private JsonNode withoutTimestamp(MvcResult result) throws Exception {
        com.fasterxml.jackson.databind.node.ObjectNode node = (com.fasterxml.jackson.databind.node.ObjectNode) readJson(result);
        node.remove("timestamp");
        return node;
    }

    private void assertNothingSecretLeaked() {
        assertThat(secretsSeen).isNotEmpty();
        List<String> logLines = logs.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
        for (String secret : secretsSeen) {
            assertThat(logLines).as("log lines containing a secret").noneMatch(line -> line.contains(secret));
            for (AuditEventEntity event : auditEvents.findAll()) {
                assertThat(String.valueOf(event.getReason())).doesNotContain(secret);
                assertThat(String.valueOf(event.getMetadata())).doesNotContain(secret);
            }
        }
    }
}
