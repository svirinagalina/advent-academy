package com.adventacademy.userservice.infrastructure.security;

import com.adventacademy.userservice.domain.model.RsaKeyPair;
import com.adventacademy.userservice.domain.repository.RsaKeyRepository;
import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JjwtProviderTest {

    private static final Instant NOW = Instant.parse("2026-08-12T10:00:00Z");
    private static final String ISSUER = "advent-academy-user-service";
    private static final String AUDIENCE = "advent-academy-services";

    private final RsaKeyRepository keyRepository = new SingleKeyRepository("test-key");

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
        final JjwtProvider expiredProvider =
                provider(keyRepository, Clock.fixed(NOW.plusSeconds(16 * 60), ZoneOffset.UTC));

        assertThat(expiredProvider.validateAccessToken(token)).isFalse();
        assertThatThrownBy(() -> expiredProvider.getUserIdFromToken(token))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void rejectsTokenSignedByAnUnknownKey() {
        final String token = provider(keyRepository, Clock.fixed(NOW, ZoneOffset.UTC))
                .generateAccessToken(UUID.randomUUID(), "user@example.com", Set.of());
        final JjwtProvider other =
                provider(new SingleKeyRepository("another-key"), Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(other.validateAccessToken(token)).isFalse();
    }

    private JjwtProvider provider(RsaKeyRepository repository, Clock clock) {
        return new JjwtProvider(repository, ISSUER, AUDIENCE, 15, 7, 30, clock);
    }

    /**
     * Minimal in-memory {@link RsaKeyRepository} holding one freshly generated, active key pair.
     * Two instances hold different keys, which is what makes an unknown-key token verifiable as such.
     */
    private static final class SingleKeyRepository implements RsaKeyRepository {

        private final RsaKeyPair keyPair;

        private SingleKeyRepository(String kid) {
            final KeyPair generated = generateKeyPair();
            this.keyPair = RsaKeyPair.create(
                    kid,
                    "RS256",
                    toPem("PRIVATE KEY", generated.getPrivate().getEncoded()),
                    toPem("PUBLIC KEY", generated.getPublic().getEncoded()));
        }

        private static KeyPair generateKeyPair() {
            try {
                final KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
                generator.initialize(2048);
                return generator.generateKeyPair();
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException("RSA key generation is not available", e);
            }
        }

        private static String toPem(String type, byte[] encoded) {
            return "-----BEGIN " + type + "-----\n"
                    + Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(encoded)
                    + "\n-----END " + type + "-----";
        }

        @Override
        public void save(RsaKeyPair pair) {
            throw new UnsupportedOperationException("read-only test repository");
        }

        @Override
        public Optional<RsaKeyPair> findByKid(String kid) {
            return keyPair.getKid().equals(kid) ? Optional.of(keyPair) : Optional.empty();
        }

        @Override
        public List<RsaKeyPair> findActiveOrRotating() {
            return List.of(keyPair);
        }

        @Override
        public List<RsaKeyPair> findActive() {
            return List.of(keyPair);
        }

        @Override
        public Optional<RsaKeyPair> findById(UUID id) {
            return keyPair.getId().equals(id) ? Optional.of(keyPair) : Optional.empty();
        }

        @Override
        public List<RsaKeyPair> findByStatus(RsaKeyPair.Status status) {
            return keyPair.getStatus() == status ? List.of(keyPair) : List.of();
        }
    }
}
