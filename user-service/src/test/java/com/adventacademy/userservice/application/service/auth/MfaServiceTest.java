package com.adventacademy.userservice.application.service.auth;

import com.adventacademy.userservice.application.dto.response.ConfirmMfaResponse;
import com.adventacademy.userservice.application.dto.response.EnableMfaResponse;
import com.adventacademy.userservice.application.port.AuditTrailPort;
import com.adventacademy.userservice.application.port.MfaProvider;
import com.adventacademy.userservice.application.port.MfaSecretCipher;
import com.adventacademy.userservice.application.port.PasswordEncoder;
import com.adventacademy.userservice.domain.exception.InvalidCredentialsException;
import com.adventacademy.userservice.domain.model.AuditEvent;
import com.adventacademy.userservice.domain.model.AuditEventType;
import com.adventacademy.userservice.domain.model.AuditResult;
import com.adventacademy.userservice.domain.model.RecoveryCode;
import com.adventacademy.userservice.domain.model.User;
import com.adventacademy.userservice.domain.repository.RecoveryCodeRepository;
import com.adventacademy.userservice.domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MfaServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RecoveryCodeRepository recoveryCodeRepository;
    @Mock private MfaProvider mfaProvider;
    @Mock private MfaSecretCipher cipher;
    @Mock private MfaCodeVerifier codeVerifier;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuditTrailPort auditTrail;

    private MfaService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new MfaService(userRepository, recoveryCodeRepository, mfaProvider, cipher, codeVerifier, passwordEncoder,
                auditTrail, Clock.fixed(Instant.parse("2026-05-28T10:00:00Z"), ZoneOffset.UTC), 8);
        user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("user@example.com");
        user.setPasswordHash("hash");
        when(userRepository.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
    }

    @Test
    void enable_requiresTheCurrentPassword() {
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

        assertThatThrownBy(() -> service.enableMfa(user.getId(), "wrong")).isInstanceOf(InvalidCredentialsException.class);

        verify(userRepository, never()).save(any());
        assertThat(lastAudit().getReason()).isEqualTo("Step-up password check failed");
    }

    @Test
    void enable_storesTheSecretEncryptedAndReturnsEnrolmentMaterialOnce() {
        when(passwordEncoder.matches("pw", "hash")).thenReturn(true);
        when(mfaProvider.generateSecret()).thenReturn("SEED");
        when(cipher.encrypt("SEED")).thenReturn("v1:n:c");
        when(mfaProvider.generateQrCodeUrl("user@example.com", "SEED")).thenReturn("otpauth://x");
        when(mfaProvider.generateQrCodeImage("user@example.com", "SEED")).thenReturn("data:image/png;base64,AAA");
        when(userRepository.save(any())).thenReturn(user);

        EnableMfaResponse response = service.enableMfa(user.getId(), "pw");

        assertThat(response.secret()).isEqualTo("SEED");
        assertThat(response.qrCodeUrl()).isEqualTo("otpauth://x");
        assertThat(user.getMfaSecret()).isEqualTo("v1:n:c");
        assertThat(user.isMfaEnabled()).isFalse(); // not until the first code verified
    }

    @Test
    void enable_refusesWhenAlreadyEnabled() {
        user.setMfaEnabled(true);
        when(passwordEncoder.matches("pw", "hash")).thenReturn(true);

        assertThatThrownBy(() -> service.enableMfa(user.getId(), "pw")).isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void confirm_turnsMfaOnAndHandsOutHashedRecoveryCodesExactlyOnce() {
        user.setMfaSecret("v1:n:c");
        when(codeVerifier.verifyTotp(user, "123456")).thenReturn(true);
        when(userRepository.save(any())).thenReturn(user);
        when(recoveryCodeRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        ConfirmMfaResponse response = service.confirmMfa(user.getId(), "123456");

        assertThat(user.isMfaEnabled()).isTrue();
        assertThat(response.recoveryCodes()).hasSize(8).allMatch(OpaqueTokens::looksLikeRecoveryCode);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RecoveryCode>> stored = ArgumentCaptor.forClass(List.class);
        verify(recoveryCodeRepository).saveAll(stored.capture());
        List<String> hashes = stored.getValue().stream().map(RecoveryCode::getCodeHash).toList();
        assertThat(hashes).hasSize(8).doesNotContainAnyElementsOf(response.recoveryCodes());
        for (String code : response.recoveryCodes()) {
            assertThat(hashes).contains(OpaqueTokens.digest(OpaqueTokens.normalizeRecoveryCode(code)));
        }
        assertThat(lastAudit().getEventType()).isEqualTo(AuditEventType.MFA_ENABLED);
    }

    @Test
    void confirm_wrongCodeKeepsMfaOff() {
        user.setMfaSecret("v1:n:c");
        when(codeVerifier.verifyTotp(user, "000000")).thenReturn(false);

        assertThatThrownBy(() -> service.confirmMfa(user.getId(), "000000")).isInstanceOf(InvalidCredentialsException.class);

        assertThat(user.isMfaEnabled()).isFalse();
        verify(recoveryCodeRepository, never()).saveAll(anyList());
        assertThat(lastAudit().getResult()).isEqualTo(AuditResult.FAILED);
    }

    @Test
    void confirm_withoutEnrolmentIsRefused() {
        assertThatThrownBy(() -> service.confirmMfa(user.getId(), "123456")).isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void disable_requiresPasswordAndASecondFactor() {
        user.setMfaEnabled(true);
        user.setMfaSecret("v1:n:c");
        when(passwordEncoder.matches("pw", "hash")).thenReturn(true);
        when(codeVerifier.verify(user, "000000")).thenReturn(MfaCodeVerifier.Outcome.REJECTED);

        assertThatThrownBy(() -> service.disableMfa(user.getId(), "pw", "000000")).isInstanceOf(InvalidCredentialsException.class);

        assertThat(user.isMfaEnabled()).isTrue();
        verify(recoveryCodeRepository, never()).deleteAllByUserId(any());
    }

    @Test
    void disable_withRecoveryCodeClearsSecretReplayGuardAndCodes() {
        user.setMfaEnabled(true);
        user.setMfaSecret("v1:n:c");
        user.setMfaLastUsedStep(42L);
        when(passwordEncoder.matches("pw", "hash")).thenReturn(true);
        when(codeVerifier.verify(user, "7K3MQ-XW9RT")).thenReturn(MfaCodeVerifier.Outcome.RECOVERY_CODE);
        when(userRepository.save(any())).thenReturn(user);

        service.disableMfa(user.getId(), "pw", "7K3MQ-XW9RT");

        assertThat(user.isMfaEnabled()).isFalse();
        assertThat(user.getMfaSecret()).isNull();
        assertThat(user.getMfaLastUsedStep()).isNull();
        verify(recoveryCodeRepository).deleteAllByUserId(user.getId());
        assertThat(lastAudit().getEventType()).isEqualTo(AuditEventType.MFA_DISABLED);
        assertThat(lastAudit().getReason()).isEqualTo("recovery code");
    }

    @Test
    void disable_whenNotEnabledIsRefused() {
        when(passwordEncoder.matches("pw", "hash")).thenReturn(true);

        assertThatThrownBy(() -> service.disableMfa(user.getId(), "pw", "123456")).isInstanceOf(InvalidCredentialsException.class);
    }

    private AuditEvent lastAudit() {
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditTrail).record(captor.capture());
        return captor.getValue();
    }
}
