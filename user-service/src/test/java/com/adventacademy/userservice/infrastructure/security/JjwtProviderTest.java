package com.adventacademy.userservice.infrastructure.security;

import com.adventacademy.userservice.domain.model.RsaKeyPair;
import com.adventacademy.userservice.domain.repository.RsaKeyRepository;
import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JjwtProviderTest {

    private static final Instant NOW = Instant.parse("2026-08-12T10:00:00Z");

    private final RsaKeyRepository keyRepository = new InMemoryRsaKeyRepository();

    @Test
    void issuesTypedTokensWithIdentityIssuerAudienceAndExpiry() {
        final UUID userId = UUID.randomUUID();
        final JjwtProvider provider = provider(keyRepository, Clock.fixed(NOW, ZoneOffset.UTC));

        final String accessToken = provider.generateAccessToken(userId, "user@example.com", Set.of("STUDENT"));
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
        final String token = provider(keyRepository, Clock.fixed(NOW, ZoneOffset.UTC))
                .generateAccessToken(userId, "user@example.com", Set.of());
        final JjwtProvider expiredProvider = provider(keyRepository, Clock.fixed(NOW.plusSeconds(16 * 60), ZoneOffset.UTC));

        assertThat(expiredProvider.validateAccessToken(token)).isFalse();
        assertThatThrownBy(() -> expiredProvider.getUserIdFromToken(token))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void rejectsTokenSignedByAKeyThisServiceDoesNotKnow() {
        final String token = provider(keyRepository, Clock.fixed(NOW, ZoneOffset.UTC))
                .generateAccessToken(UUID.randomUUID(), "user@example.com", Set.of());
        final JjwtProvider other = provider(new InMemoryRsaKeyRepository(), Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(other.validateAccessToken(token)).isFalse();
    }

    private JjwtProvider provider(RsaKeyRepository repository, Clock clock) {
        return new JjwtProvider(
                repository,
                "advent-academy-user-service",
                "advent-academy-services",
                15,
                7,
                30,
                clock);
    }

    /** Minimal in-memory key store seeded with a single freshly generated active RSA key. */
    private static final class InMemoryRsaKeyRepository implements RsaKeyRepository {

        private final List<RsaKeyPair> keys = new ArrayList<>();

        private InMemoryRsaKeyRepository() {
            keys.add(new RsaKeyPairGenerator().generateNewKeyPair());
        }

        @Override
        public void save(RsaKeyPair keyPair) {
            keys.add(keyPair);
        }

        @Override
        public Optional<RsaKeyPair> findByKid(String kid) {
            return keys.stream().filter(key -> key.getKid().equals(kid)).findFirst();
        }

        @Override
        public List<RsaKeyPair> findActiveOrRotating() {
            return keys.stream().filter(RsaKeyPair::isActiveOrRotating).toList();
        }

        @Override
        public List<RsaKeyPair> findActive() {
            return keys.stream().filter(RsaKeyPair::isActive).toList();
        }

        @Override
        public Optional<RsaKeyPair> findById(UUID id) {
            return keys.stream().filter(key -> key.getId().equals(id)).findFirst();
        }

        @Override
        public List<RsaKeyPair> findByStatus(RsaKeyPair.Status status) {
            return keys.stream().filter(key -> key.getStatus() == status).toList();
        }
    }
}
