package com.adventacademy.userservice.infrastructure.security;

import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JjwtProviderTest {

    private static final String SECRET = "test-secret-key-for-testing-purposes-only-32-bytes";
    private static final Instant NOW = Instant.parse("2026-08-12T10:00:00Z");

    @Test
    void issuesTypedTokensWithIdentityIssuerAudienceAndExpiry() {
        final UUID userId = UUID.randomUUID();
        final JjwtProvider provider = provider(Clock.fixed(NOW, ZoneOffset.UTC));

        final String accessToken = provider.generateAccessToken(userId, "user@example.com", java.util.Set.of("STUDENT"));
        final String refreshToken = provider.generateRefreshToken(userId);

        assertThat(provider.validateAccessToken(accessToken)).isTrue();
        assertThat(provider.validateRefreshToken(accessToken)).isFalse();
        assertThat(provider.validateRefreshToken(refreshToken)).isTrue();
        assertThat(provider.validateAccessToken(refreshToken)).isFalse();
        assertThat(provider.getUserIdFromToken(accessToken)).isEqualTo(userId);
        assertThat(provider.getRolesFromToken(accessToken)).containsExactly("STUDENT");
        assertThat(provider.consumeRefreshToken(refreshToken)).isTrue();
        assertThat(provider.consumeRefreshToken(refreshToken)).isFalse();
    }

    @Test
    void rejectsExpiredTokenAfterClockSkewWindow() {
        final UUID userId = UUID.randomUUID();
        final String token = provider(Clock.fixed(NOW, ZoneOffset.UTC))
                .generateAccessToken(userId, "user@example.com", java.util.Set.of());
        final JjwtProvider expiredProvider = provider(Clock.fixed(NOW.plusSeconds(16 * 60), ZoneOffset.UTC));

        assertThat(expiredProvider.validateAccessToken(token)).isFalse();
        assertThatThrownBy(() -> expiredProvider.getUserIdFromToken(token))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void rejectsWrongSigningKeyAndShortSecrets() {
        final String token = provider(Clock.fixed(NOW, ZoneOffset.UTC))
                .generateAccessToken(UUID.randomUUID(), "user@example.com", java.util.Set.of());
        final JjwtProvider other = new JjwtProvider(
                "another-test-secret-key-that-is-long-enough-32-bytes",
                "advent-academy-user-service",
                "advent-academy-services",
                15,
                7,
                30,
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(other.validateAccessToken(token)).isFalse();
        assertThatThrownBy(() -> new JjwtProvider(
                "short",
                "issuer",
                "audience",
                15,
                7,
                30,
                Clock.systemUTC()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private JjwtProvider provider(Clock clock) {
        return new JjwtProvider(
                SECRET,
                "advent-academy-user-service",
                "advent-academy-services",
                15,
                7,
                30,
                clock);
    }
}
