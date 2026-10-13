package com.adventacademy.userservice.infrastructure.security;

import io.jsonwebtoken.ExpiredJwtException;
import com.adventacademy.userservice.application.port.RefreshTokenStore;
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
                Clock.fixed(NOW, ZoneOffset.UTC),
                new TestRefreshTokenStore());

        assertThat(other.validateAccessToken(token)).isFalse();
        assertThatThrownBy(() -> new JjwtProvider(
                "short",
                "issuer",
                "audience",
                15,
                7,
                30,
                Clock.systemUTC(),
                new TestRefreshTokenStore()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static class TestRefreshTokenStore implements RefreshTokenStore {
        private final java.util.Map<String, Entry> tokens = new java.util.HashMap<>();
        private record Entry(UUID userId, Instant expiresAt) {}

        public void save(String digest, UUID userId, Instant expiresAt) {
            tokens.put(digest, new Entry(userId, expiresAt));
        }

        public boolean isActive(String digest, Instant now) {
            return tokens.containsKey(digest) && tokens.get(digest).expiresAt().isAfter(now);
        }

        public boolean consume(String digest, Instant now) {
            return isActive(digest, now) && tokens.remove(digest) != null;
        }

        public void revokeByUserId(UUID userId) {
            tokens.values().removeIf(entry -> entry.userId().equals(userId));
        }
    }

    private JjwtProvider provider(Clock clock) {
        return new JjwtProvider(
                SECRET,
                "advent-academy-user-service",
                "advent-academy-services",
                15,
                7,
                30,
                clock,
                new TestRefreshTokenStore());
    }
}
