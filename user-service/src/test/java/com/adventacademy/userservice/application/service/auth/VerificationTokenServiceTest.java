package com.adventacademy.userservice.application.service.auth;

import com.adventacademy.userservice.domain.exception.InvalidTokenException;
import com.adventacademy.userservice.domain.model.VerificationToken;
import com.adventacademy.userservice.domain.repository.VerificationTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VerificationTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-05-28T10:00:00Z");
    private static final UUID USER = UUID.randomUUID();

    @Mock private VerificationTokenRepository repository;

    private VerificationTokenService service;

    @BeforeEach
    void setUp() {
        service = new VerificationTokenService(repository, Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofHours(24), Duration.ofHours(1));
    }

    @Test
    void issue_retiresThePreviousActiveTokenAndStoresOnlyTheDigest() {
        String plaintext = service.issue(USER, VerificationToken.PURPOSE_PASSWORD_RESET);

        verify(repository).invalidateActive(USER, VerificationToken.PURPOSE_PASSWORD_RESET, NOW);
        ArgumentCaptor<VerificationToken> saved = ArgumentCaptor.forClass(VerificationToken.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getTokenDigest()).isEqualTo(OpaqueTokens.digest(plaintext)).isNotEqualTo(plaintext);
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(NOW.plus(Duration.ofHours(1)));
        assertThat(saved.getValue().getPurpose()).isEqualTo(VerificationToken.PURPOSE_PASSWORD_RESET);
    }

    @Test
    void issue_emailVerificationGetsTheLongerTtl() {
        service.issue(USER, VerificationToken.PURPOSE_EMAIL_VERIFICATION);

        ArgumentCaptor<VerificationToken> saved = ArgumentCaptor.forClass(VerificationToken.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(NOW.plus(Duration.ofHours(24)));
    }

    @Test
    void redeem_validToken_consumesItAtomically() {
        VerificationToken token = stored(VerificationToken.PURPOSE_PASSWORD_RESET, NOW.plusSeconds(60), null);
        when(repository.consume(token.getId(), NOW)).thenReturn(true);

        VerificationToken redeemed = service.redeem("plain", VerificationToken.PURPOSE_PASSWORD_RESET);

        assertThat(redeemed.getUserId()).isEqualTo(USER);
        assertThat(redeemed.isUsed()).isTrue();
    }

    @Test
    void redeem_unknownToken() {
        when(repository.findByTokenDigest(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.redeem("nope", VerificationToken.PURPOSE_PASSWORD_RESET))
                .isInstanceOf(InvalidTokenException.class)
                .extracting("reason").isEqualTo("unknown");
        assertThatThrownBy(() -> service.redeem("", VerificationToken.PURPOSE_PASSWORD_RESET))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void redeem_expiredToken() {
        stored(VerificationToken.PURPOSE_PASSWORD_RESET, NOW.minusSeconds(1), null);

        assertThatThrownBy(() -> service.redeem("plain", VerificationToken.PURPOSE_PASSWORD_RESET))
                .isInstanceOf(InvalidTokenException.class)
                .extracting("reason").isEqualTo("expired");
        verify(repository, never()).consume(any(), any());
    }

    @Test
    void redeem_alreadyUsedToken() {
        stored(VerificationToken.PURPOSE_PASSWORD_RESET, NOW.plusSeconds(60), NOW.minusSeconds(30));

        assertThatThrownBy(() -> service.redeem("plain", VerificationToken.PURPOSE_PASSWORD_RESET))
                .isInstanceOf(InvalidTokenException.class)
                .extracting("reason").isEqualTo("used");
    }

    @Test
    void redeem_emailVerificationTokenCannotResetAPassword() {
        stored(VerificationToken.PURPOSE_EMAIL_VERIFICATION, NOW.plusSeconds(60), null);

        assertThatThrownBy(() -> service.redeem("plain", VerificationToken.PURPOSE_PASSWORD_RESET))
                .isInstanceOf(InvalidTokenException.class)
                .extracting("reason").isEqualTo("wrong_purpose");
        verify(repository, never()).consume(any(), any());
    }

    @Test
    void redeem_losingTheRaceToAConcurrentRequestCountsAsUsed() {
        VerificationToken token = stored(VerificationToken.PURPOSE_PASSWORD_RESET, NOW.plusSeconds(60), null);
        when(repository.consume(token.getId(), NOW)).thenReturn(false);

        assertThatThrownBy(() -> service.redeem("plain", VerificationToken.PURPOSE_PASSWORD_RESET))
                .isInstanceOf(InvalidTokenException.class)
                .extracting("reason").isEqualTo("used");
    }

    @Test
    void publicMessageDoesNotDistinguishTheReasons() {
        assertThat(new InvalidTokenException("expired").getMessage())
                .isEqualTo(new InvalidTokenException("used").getMessage())
                .isEqualTo(new InvalidTokenException("wrong_purpose").getMessage())
                .isEqualTo(new InvalidTokenException("unknown").getMessage());
    }

    private VerificationToken stored(String purpose, Instant expiresAt, Instant usedAt) {
        VerificationToken token = VerificationToken.restore(UUID.randomUUID(), USER, OpaqueTokens.digest("plain"), purpose,
                NOW.minusSeconds(120), expiresAt, usedAt);
        when(repository.findByTokenDigest(OpaqueTokens.digest("plain"))).thenReturn(Optional.of(token));
        return token;
    }
}
