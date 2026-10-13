package com.adventacademy.userservice.application.service.auth;

import com.adventacademy.userservice.application.port.MfaProvider;
import com.adventacademy.userservice.application.port.MfaSecretCipher;
import com.adventacademy.userservice.domain.model.User;
import com.adventacademy.userservice.domain.repository.RecoveryCodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.OptionalLong;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MfaCodeVerifierTest {

    private static final Instant NOW = Instant.parse("2026-05-28T10:00:00Z");

    @Mock private MfaProvider mfaProvider;
    @Mock private MfaSecretCipher cipher;
    @Mock private RecoveryCodeRepository recoveryCodes;

    private MfaCodeVerifier verifier;
    private User user;

    @BeforeEach
    void setUp() {
        verifier = new MfaCodeVerifier(mfaProvider, cipher, recoveryCodes, Clock.fixed(NOW, ZoneOffset.UTC));
        user = new User();
        user.setId(UUID.randomUUID());
        user.setMfaEnabled(true);
        user.setMfaSecret("v1:nonce:cipher");
    }

    @Test
    void validTotpAdvancesTheReplayGuard() {
        when(cipher.decrypt("v1:nonce:cipher")).thenReturn("SEED");
        when(mfaProvider.verifyCode("SEED", "123456")).thenReturn(OptionalLong.of(1000L));

        assertThat(verifier.verify(user, "123456")).isEqualTo(MfaCodeVerifier.Outcome.TOTP);
        assertThat(user.getMfaLastUsedStep()).isEqualTo(1000L);
        verifyNoInteractions(recoveryCodes);
    }

    @Test
    void replayingTheSameStepIsRefusedEvenThoughTheCodeIsStillInsideTheWindow() {
        user.setMfaLastUsedStep(1000L);
        when(cipher.decrypt(anyString())).thenReturn("SEED");
        when(mfaProvider.verifyCode("SEED", "123456")).thenReturn(OptionalLong.of(1000L));

        assertThat(verifier.verify(user, "123456")).isEqualTo(MfaCodeVerifier.Outcome.REJECTED);
        assertThat(user.getMfaLastUsedStep()).isEqualTo(1000L);
    }

    @Test
    void anOlderStepThanTheLastAcceptedOneIsRefused() {
        user.setMfaLastUsedStep(1001L);
        when(cipher.decrypt(anyString())).thenReturn("SEED");
        when(mfaProvider.verifyCode("SEED", "111111")).thenReturn(OptionalLong.of(1000L));

        assertThat(verifier.verify(user, "111111")).isEqualTo(MfaCodeVerifier.Outcome.REJECTED);
    }

    @Test
    void wrongTotpIsRejected() {
        when(cipher.decrypt(anyString())).thenReturn("SEED");
        when(mfaProvider.verifyCode("SEED", "000000")).thenReturn(OptionalLong.empty());

        assertThat(verifier.verify(user, "000000")).isEqualTo(MfaCodeVerifier.Outcome.REJECTED);
        assertThat(user.getMfaLastUsedStep()).isNull();
    }

    @Test
    void undecryptableSecretIsRejectedNotCrashed() {
        when(cipher.decrypt(anyString())).thenThrow(new IllegalArgumentException("unknown key"));

        assertThat(verifier.verify(user, "123456")).isEqualTo(MfaCodeVerifier.Outcome.REJECTED);
    }

    @Test
    void recoveryCodeIsNormalisedHashedAndBurntAtomically() {
        String expectedHash = OpaqueTokens.digest("7K3MQXW9RT");
        when(recoveryCodes.consume(user.getId(), expectedHash, NOW)).thenReturn(true);

        assertThat(verifier.verify(user, " 7k3mq-xw9rt ")).isEqualTo(MfaCodeVerifier.Outcome.RECOVERY_CODE);
        verifyNoInteractions(mfaProvider, cipher);
    }

    @Test
    void alreadyBurntRecoveryCodeIsRejected() {
        when(recoveryCodes.consume(eq(user.getId()), anyString(), any())).thenReturn(false);

        assertThat(verifier.verify(user, "7K3MQ-XW9RT")).isEqualTo(MfaCodeVerifier.Outcome.REJECTED);
    }

    @Test
    void blankCodeOrMissingSecretIsRejectedUpFront() {
        assertThat(verifier.verify(user, "")).isEqualTo(MfaCodeVerifier.Outcome.REJECTED);
        user.setMfaSecret(null);
        assertThat(verifier.verify(user, "123456")).isEqualTo(MfaCodeVerifier.Outcome.REJECTED);
        verify(recoveryCodes, never()).consume(any(), any(), any());
    }
}
