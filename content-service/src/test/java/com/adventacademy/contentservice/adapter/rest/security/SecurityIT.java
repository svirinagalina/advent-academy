package com.adventacademy.contentservice.adapter.rest.security;

import com.adventacademy.contentservice.AbstractPostgresIT;
import com.adventacademy.contentservice.domain.ContentStatus;
import com.adventacademy.contentservice.domain.LessonContent;
import com.adventacademy.contentservice.domain.repository.LessonContentRepository;
import com.adventacademy.contentservice.infrastructure.persistence.entity.ContentAuditEventEntity;
import com.adventacademy.contentservice.infrastructure.persistence.jpa.ContentAuditEventJpaRepository;
import com.jayway.jsonpath.JsonPath;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security integration tests for issue #44: content-service authorization.
 * <p>
 * Test matrix:
 * <ol>
 *     <li>authentication: anonymous, malformed and invalid tokens (signature, issuer, audience,
 *     token type, expiry, unsecured {@code alg: none}) are refused with 401;</li>
 *     <li>role contract: creating constructor objects and using the Markdown preview is limited to
 *     {@code CONTENT_EDITOR}/{@code ADMIN}, a learner reaches neither;</li>
 *     <li>object-level authorization: an {@code INSTRUCTOR} cannot change content owned by
 *     somebody else, while the owner and curators can, and the refused change leaves the object
 *     untouched;</li>
 *     <li>assets: published material is downloadable anonymously, draft material is not;</li>
 *     <li>audit: both the successful publication and the refused cross-owner write end up in
 *     {@code content_audit_events} with the acting user and the result.</li>
 * </ol>
 */
@AutoConfigureMockMvc
@AutoConfigureObservability
public class SecurityIT extends AbstractPostgresIT {

    private static final String ROLE_CONTENT_EDITOR = "CONTENT_EDITOR";
    private static final String ROLE_INSTRUCTOR = "INSTRUCTOR";
    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_LEARNER = "LEARNER";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private LessonContentRepository lessonContentRepository;

    @Autowired
    private ContentAuditEventJpaRepository auditEvents;

    @Value("${security.jwt.secret}")
    private String jwtSecret;

    @Value("${security.jwt.issuer}")
    private String issuer;

    @Value("${security.jwt.audience}")
    private String audience;

    // ---------------------------------------------------------------- authentication

    @Test
    void anonymousAccessReturnsUnauthorized() throws Exception {
        mvc.perform(get("/api/courses"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    void noAuthorizationHeaderReturnsUnauthorized() throws Exception {
        mvc.perform(get("/api/components"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void malformedAuthorizationHeaderReturnsUnauthorized() throws Exception {
        mvc.perform(get("/api/courses").header(HttpHeaders.AUTHORIZATION, "NotBearer invalid"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validTokenAllowsAccess() throws Exception {
        mvc.perform(get("/api/courses").header(HttpHeaders.AUTHORIZATION, bearer(token(UUID.randomUUID(), ROLE_CONTENT_EDITOR))))
                .andExpect(status().isOk());
    }

    @Test
    void expiredTokenReturnsUnauthorized() throws Exception {
        Instant now = Instant.now();
        String expired = signed(UUID.randomUUID(), List.of(ROLE_CONTENT_EDITOR), issuer, audience, "access",
                now.minus(1, ChronoUnit.HOURS), now.minus(30, ChronoUnit.MINUTES), signingKey());

        mvc.perform(get("/api/courses").header(HttpHeaders.AUTHORIZATION, bearer(expired)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidSignatureReturnsUnauthorized() throws Exception {
        SecretKey wrongKey = Keys.hmacShaKeyFor(
                "wrong-secret-key-that-is-long-enough-for-hs256-but-still-invalid".getBytes(StandardCharsets.UTF_8));
        String token = signed(UUID.randomUUID(), List.of(ROLE_CONTENT_EDITOR), issuer, audience, "access",
                Instant.now(), Instant.now().plus(15, ChronoUnit.MINUTES), wrongKey);

        mvc.perform(get("/api/courses").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongIssuerReturnsUnauthorized() throws Exception {
        String token = signed(UUID.randomUUID(), List.of(ROLE_CONTENT_EDITOR), "https://evil.example", audience, "access",
                Instant.now(), Instant.now().plus(15, ChronoUnit.MINUTES), signingKey());

        mvc.perform(get("/api/courses").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongAudienceReturnsUnauthorized() throws Exception {
        String token = signed(UUID.randomUUID(), List.of(ROLE_CONTENT_EDITOR), issuer, "wrong-audience", "access",
                Instant.now(), Instant.now().plus(15, ChronoUnit.MINUTES), signingKey());

        mvc.perform(get("/api/courses").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshTokenIsNotAcceptedAsAccessToken() throws Exception {
        String token = signed(UUID.randomUUID(), List.of(ROLE_CONTENT_EDITOR), issuer, audience, "refresh",
                Instant.now(), Instant.now().plus(15, ChronoUnit.MINUTES), signingKey());

        mvc.perform(get("/api/courses").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isUnauthorized());
    }

    /** The classic {@code alg: none} downgrade: an unsecured JWT must never authenticate. */
    @Test
    void unsecuredAlgorithmNoneTokenReturnsUnauthorized() throws Exception {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        String header = encoder.encodeToString("{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payload = encoder.encodeToString(("{\"sub\":\"" + UUID.randomUUID() + "\",\"token_type\":\"access\",\"iss\":\""
                + issuer + "\",\"aud\":\"" + audience + "\",\"roles\":[\"ADMIN\"],\"exp\":"
                + Instant.now().plus(15, ChronoUnit.MINUTES).getEpochSecond() + "}").getBytes(StandardCharsets.UTF_8));

        mvc.perform(get("/api/courses").header(HttpHeaders.AUTHORIZATION, bearer(header + "." + payload + ".")))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------- public endpoints

    @Test
    void prometheusEndpointAllowsAnonymousAccess() throws Exception {
        mvc.perform(get("/actuator/prometheus")).andExpect(status().isOk());
    }

    @Test
    void healthEndpointAllowsAnonymousAccess() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    // ---------------------------------------------------------------- role contract

    @Test
    void instructorMayNotCreateConstructorObjects() throws Exception {
        String instructor = bearer(token(UUID.randomUUID(), ROLE_INSTRUCTOR));

        mvc.perform(post("/api/components").header(HttpHeaders.AUTHORIZATION, instructor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"Mine\", \"description\": \"d\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/courses").header(HttpHeaders.AUTHORIZATION, instructor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"Mine\", \"description\": \"d\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/directions").header(HttpHeaders.AUTHORIZATION, instructor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"Mine\", \"description\": \"d\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void learnerReachesNeitherConstructorNorMarkdownPreview() throws Exception {
        String learner = bearer(token(UUID.randomUUID(), ROLE_LEARNER));

        mvc.perform(get("/api/components").header(HttpHeaders.AUTHORIZATION, learner))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/courses").header(HttpHeaders.AUTHORIZATION, learner))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/directions").header(HttpHeaders.AUTHORIZATION, learner))
                .andExpect(status().isForbidden());
        mvc.perform(multipart("/api/markdown/preview").file(markdownFile())
                        .header(HttpHeaders.AUTHORIZATION, learner))
                .andExpect(status().isForbidden());
        mvc.perform(get("/markdown-preview/index.html").header(HttpHeaders.AUTHORIZATION, learner))
                .andExpect(status().isForbidden());
    }

    @Test
    void markdownPreviewPageIsNotPublic() throws Exception {
        mvc.perform(get("/markdown-preview/index.html")).andExpect(status().isUnauthorized());
    }

    @Test
    void catalogStaysPublic() throws Exception {
        mvc.perform(get("/api/catalog")).andExpect(status().isOk());
    }

    // ---------------------------------------------------------------- object-level authorization

    @Test
    void foreignInstructorCannotModifyComponentAndLeavesItUntouched() throws Exception {
        UUID ownerId = UUID.randomUUID();
        String owner = bearer(token(ownerId, ROLE_CONTENT_EDITOR));
        UUID componentId = createComponent(owner, "Original title");

        UUID intruderId = UUID.randomUUID();
        mvc.perform(patch("/api/components/{id}", componentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token(intruderId, ROLE_INSTRUCTOR)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"Hijacked\", \"description\": \"d\"}"))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/components/{id}", componentId).header(HttpHeaders.AUTHORIZATION, owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Original title"));

        assertThat(auditEventsFor("component:" + componentId))
                .anyMatch(event -> "DENIED".equals(event.getResult())
                        && "RENAME_COMPONENT".equals(event.getOperation())
                        && intruderId.equals(event.getActorId()));
    }

    @Test
    void foreignInstructorCannotUploadIntoSomebodyElsesLesson() throws Exception {
        String owner = bearer(token(UUID.randomUUID(), ROLE_CONTENT_EDITOR));
        UUID lessonId = addLesson(owner, createComponent(owner, "Owned component"));

        mvc.perform(multipart("/api/lessons/{lessonId}/assets", lessonId).file(assetFile())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token(UUID.randomUUID(), ROLE_INSTRUCTOR))))
                .andExpect(status().isForbidden());
    }

    @Test
    void ownerAndAdminMayModifyTheComponent() throws Exception {
        UUID ownerId = UUID.randomUUID();
        String owner = bearer(token(ownerId, ROLE_CONTENT_EDITOR));
        UUID componentId = createComponent(owner, "Original title");

        mvc.perform(patch("/api/components/{id}", componentId).header(HttpHeaders.AUTHORIZATION, owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"Renamed by owner\", \"description\": \"d\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Renamed by owner"));

        mvc.perform(patch("/api/components/{id}", componentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token(UUID.randomUUID(), ROLE_ADMIN)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"Renamed by admin\", \"description\": \"d\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Renamed by admin"));
    }

    // ---------------------------------------------------------------- assets

    @Test
    void publishedAssetIsDownloadableAnonymously() throws Exception {
        UUID ownerId = UUID.randomUUID();
        String owner = bearer(token(ownerId, ROLE_CONTENT_EDITOR));
        UUID componentId = createComponent(owner, "Published component");
        UUID lessonId = addLesson(owner, componentId);
        UUID assetId = uploadAsset(owner, lessonId);
        publishLessonContent(lessonId);

        mvc.perform(post("/api/components/{id}/publish", componentId).header(HttpHeaders.AUTHORIZATION, owner))
                .andExpect(status().isOk());

        mvc.perform(get("/api/assets/{id}", assetId))
                .andExpect(status().isOk());

        assertThat(auditEventsFor("component:" + componentId))
                .anyMatch(event -> "SUCCESS".equals(event.getResult())
                        && "PUBLISH_COMPONENT".equals(event.getOperation())
                        && ownerId.equals(event.getActorId()));
    }

    @Test
    void draftAssetIsRefusedForAnonymousAndForeignCallers() throws Exception {
        String owner = bearer(token(UUID.randomUUID(), ROLE_CONTENT_EDITOR));
        UUID lessonId = addLesson(owner, createComponent(owner, "Draft component"));
        UUID assetId = uploadAsset(owner, lessonId);

        mvc.perform(get("/api/assets/{id}", assetId))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/assets/{id}", assetId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token(UUID.randomUUID(), ROLE_INSTRUCTOR))))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/assets/{id}", assetId).header(HttpHeaders.AUTHORIZATION, owner))
                .andExpect(status().isOk());
    }

    // ---------------------------------------------------------------- fixtures

    private UUID createComponent(String bearerHeader, String title) throws Exception {
        String body = mvc.perform(post("/api/components").header(HttpHeaders.AUTHORIZATION, bearerHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"" + title + "\", \"description\": \"About it\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    private UUID addLesson(String bearerHeader, UUID componentId) throws Exception {
        String body = mvc.perform(post("/api/components/{id}/lessons", componentId)
                        .header(HttpHeaders.AUTHORIZATION, bearerHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"Lesson\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.lessons[0].id"));
    }

    private UUID uploadAsset(String bearerHeader, UUID lessonId) throws Exception {
        String body = mvc.perform(multipart("/api/lessons/{lessonId}/assets", lessonId).file(assetFile())
                        .header(HttpHeaders.AUTHORIZATION, bearerHeader))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    /** There is no status endpoint yet, so the lesson content is published through the repository. */
    private void publishLessonContent(UUID lessonId) {
        LessonContent content = LessonContent.create(lessonId, "# Lesson");
        content.changeStatus(ContentStatus.REVIEW);
        content.changeStatus(ContentStatus.PUBLISHED);
        lessonContentRepository.save(content);
    }

    private List<ContentAuditEventEntity> auditEventsFor(String target) {
        return auditEvents.findAll().stream()
                .filter(event -> target.equals(event.getTarget()))
                .toList();
    }

    private static MockMultipartFile assetFile() {
        return new MockMultipartFile("file", "diagram.png", "image/png", "fake-png-bytes".getBytes(StandardCharsets.UTF_8));
    }

    private static MockMultipartFile markdownFile() {
        return new MockMultipartFile("file", "lesson.md", "text/markdown", "# Title".getBytes(StandardCharsets.UTF_8));
    }

    // ---------------------------------------------------------------- tokens

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    private String token(UUID userId, String... roles) {
        Instant now = Instant.now();
        return signed(userId, List.of(roles), issuer, audience, "access", now, now.plus(15, ChronoUnit.MINUTES), signingKey());
    }

    private static String signed(UUID userId, List<String> roles, String issuer, String audience, String tokenType,
                                 Instant issuedAt, Instant expiration, SecretKey key) {
        return Jwts.builder()
                .subject(userId.toString())
                .issuer(issuer)
                .audience().add(audience).and()
                .claim("token_type", tokenType)
                .claim("roles", roles)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiration))
                .signWith(key)
                .compact();
    }
}
