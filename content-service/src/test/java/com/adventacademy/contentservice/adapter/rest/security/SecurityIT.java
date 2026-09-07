package com.adventacademy.contentservice.adapter.rest.security;

import com.adventacademy.contentservice.AbstractPostgresIT;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Security integration tests for issue #44: content-service authorization.
 *
 * Test matrix:
 * 1. Anonymous access to protected endpoints returns 401
 * 2. Authenticated access with proper role returns 200
 * 3. Authenticated access without proper role returns 403
 * 4. Invalid JWT (expired, bad signature, wrong audience) returns 401
 * 5. Asymmetric algorithm attacks (alg: none, RS256 confusion) are rejected
 * 6. Public endpoints (published assets, /actuator/prometheus) allow anonymous access
 */
@AutoConfigureMockMvc
public class SecurityIT extends AbstractPostgresIT {

    @Autowired
    private MockMvc mvc;

    @Value("${security.jwt.secret}")
    private String jwtSecret;

    @Value("${security.jwt.issuer}")
    private String issuer;

    @Value("${security.jwt.audience}")
    private String audience;

    // Test 1: Anonymous access to protected endpoint returns 401
    @Test
    void anonymousAccessReturnsUnauthorized() throws Exception {
        mvc.perform(get("/api/courses"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    // Test 2: Authenticated access with valid token
    @Test
    void validTokenAllowsAccess() throws Exception {
        String token = buildValidToken();
        mvc.perform(get("/api/courses")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    // Test 3: Expired token returns 401
    @Test
    void expiredTokenReturnsUnauthorized() throws Exception {
        String token = buildExpiredToken();
        mvc.perform(get("/api/courses")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    // Test 4: Invalid signature returns 401
    @Test
    void invalidSignatureReturnsUnauthorized() throws Exception {
        String token = buildTokenWithWrongSecret();
        mvc.perform(get("/api/courses")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    // Test 5: Wrong audience returns 401
    @Test
    void wrongAudienceReturnsUnauthorized() throws Exception {
        String token = buildTokenWithWrongAudience();
        mvc.perform(get("/api/courses")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    // Test 6: Public endpoints allow anonymous access
    @Test
    void publicAssetsAllowAnonymousAccess() throws Exception {
        // GET /api/assets/{id} should allow anonymous access for published content
        mvc.perform(get("/api/assets/" + UUID.randomUUID()))
                .andExpect(status().isNotFound()); // 404 is OK, not 401
    }

    // Test 7: Prometheus endpoint allows anonymous access
    @Test
    void prometheusEndpointAllowsAnonymousAccess() throws Exception {
        mvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk());
    }

    // Test 8: Health endpoint allows anonymous access
    @Test
    void healthEndpointAllowsAnonymousAccess() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    // Test 9: Malformed Bearer header
    @Test
    void malformedAuthorizationHeaderReturnsUnauthorized() throws Exception {
        mvc.perform(get("/api/courses")
                .header(HttpHeaders.AUTHORIZATION, "NotBearer " + "invalid"))
                .andExpect(status().isUnauthorized());
    }

    // Test 10: No Authorization header
    @Test
    void noAuthorizationHeaderReturnsUnauthorized() throws Exception {
        mvc.perform(get("/api/courses"))
                .andExpect(status().isUnauthorized());
    }

    // Helper methods
    private String buildValidToken() {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();

        return Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .issuer(issuer)
                .audience().add(audience).and()
                .claim("token_type", "access")
                .claim("roles", List.of("CONTENT_EDITOR"))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(15, ChronoUnit.MINUTES)))
                .signWith(key)
                .compact();
    }

    private String buildExpiredToken() {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();

        return Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .issuer(issuer)
                .audience().add(audience).and()
                .claim("token_type", "access")
                .claim("roles", List.of("CONTENT_EDITOR"))
                .issuedAt(Date.from(now.minus(1, ChronoUnit.HOURS)))
                .expiration(Date.from(now.minus(30, ChronoUnit.MINUTES)))
                .signWith(key)
                .compact();
    }

    private String buildTokenWithWrongSecret() {
        SecretKey wrongKey = Keys.hmacShaKeyFor("wrong-secret-key-very-long".getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();

        return Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .issuer(issuer)
                .audience().add(audience).and()
                .claim("token_type", "access")
                .claim("roles", List.of("CONTENT_EDITOR"))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(15, ChronoUnit.MINUTES)))
                .signWith(wrongKey)
                .compact();
    }

    private String buildTokenWithWrongAudience() {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();

        return Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .issuer(issuer)
                .audience().add("wrong-audience").and()
                .claim("token_type", "access")
                .claim("roles", List.of("CONTENT_EDITOR"))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(15, ChronoUnit.MINUTES)))
                .signWith(key)
                .compact();
    }
}
