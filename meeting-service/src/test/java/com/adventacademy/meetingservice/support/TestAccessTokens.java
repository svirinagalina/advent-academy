package com.adventacademy.meetingservice.support;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

public final class TestAccessTokens {

    private static final String SECRET = "test-secret-key-for-testing-purposes-only-32-bytes";

    private TestAccessTokens() {
    }

    public static String accessToken(UUID userId) {
        return token(userId, "access", Instant.now().plus(Duration.ofMinutes(5)));
    }

    public static String refreshToken(UUID userId) {
        return token(userId, "refresh", Instant.now().plus(Duration.ofMinutes(5)));
    }

    public static String expiredAccessToken(UUID userId) {
        return token(userId, "access", Instant.now().minus(Duration.ofMinutes(5)));
    }

    private static String token(UUID userId, String type, Instant expiresAt) {
        final Instant issuedAt = expiresAt.minus(Duration.ofMinutes(5));
        return Jwts.builder()
                .subject(userId.toString())
                .issuer("advent-academy-user-service")
                .audience().add("advent-academy-services").and()
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .id(UUID.randomUUID().toString())
                .claim("token_type", type)
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }
}
