package com.adventacademy.userservice.application.service.auth;

import com.adventacademy.userservice.application.dto.request.CompletePasswordResetRequest;
import com.adventacademy.userservice.application.dto.request.PasswordResetRequest;
import com.adventacademy.userservice.application.port.AuditTrailPort;
import com.adventacademy.userservice.application.port.JwtProvider;
import com.adventacademy.userservice.application.port.PasswordEncoder;
import com.adventacademy.userservice.domain.exception.InvalidTokenException;
import com.adventacademy.userservice.domain.model.AuditEvent;
import com.adventacademy.userservice.domain.model.AuditEventType;
import com.adventacademy.userservice.domain.model.AuditResult;
import com.adventacademy.userservice.domain.model.User;
import com.adventacademy.userservice.domain.model.UserStatus;
import com.adventacademy.userservice.domain.model.VerificationToken;
import com.adventacademy.userservice.domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    private static final Instant NOW = Instant.parse("2026-05-28T10:00:00Z");

    @Mock private UserRepository userRepository;
    @Mock private VerificationTokenService tokens;
    @Mock private EmailDispatcher emails;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtProvider jwtProvider;
    @Mock private AuditTrailPort auditTrailPort;

    private PasswordResetService service;
    private final Clock clock = Clock.fixed(NOW, ZoneId.of("UTC"));

    @BeforeEach
    void setUp() {
        service = new PasswordResetService(userRepository, tokens, emails, passwordEncoder, jwtProvider, auditTrailPort, clock);
    }

    @Test
    void initiate_knownEmail_issuesResetTokenMailsItAndRecordsSuccess() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("known@test.com");
        when(userRepository.findByEmail("known@test.com")).thenReturn(Optional.of(user));
        when(tokens.issue(user.getId(), VerificationToken.PURPOSE_PASSWORD_RESET)).thenReturn("reset-token");

        service.initiate(new PasswordResetRequest("known@test.com", "10.0.0.1", "Mozilla"));

        verify(emails).sendPasswordReset(user.getId(), "known@test.com", "reset-token");
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditTrailPort).record(captor.capture());
        AuditEvent event = captor.getValue();
        assertThat(event.getEventType()).isEqualTo(AuditEventType.PASSWORD_RESET_INITIATED);
        assertThat(event.getResult()).isEqualTo(AuditResult.SUCCESS);
        assertThat(event.getActorUserId()).isEqualTo(user.getId());
        assertThat(event.getIpAddress()).isEqualTo("10.0.0.1");
        assertThat(event.getUserAgent()).isEqualTo("Mozilla");
    }

    @Test
    void initiate_unknownEmail_recordsFailedEventAndSendsNothing() {
        when(userRepository.findByEmail("ghost@test.com")).thenReturn(Optional.empty());

        service.initiate(new PasswordResetRequest("ghost@test.com", "10.0.0.1", "curl/8"));

        verifyNoInteractions(tokens, emails);
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditTrailPort).record(captor.capture());
        assertThat(captor.getValue().getResult()).isEqualTo(AuditResult.FAILED);
        assertThat(captor.getValue().getReason()).isEqualTo("Unknown email");
        assertThat(captor.getValue().getActorUserId()).isNull();
    }

    @Test
    void initiate_responseIsIdenticalForKnownAndUnknownEmail() {
        User user = new User();
        user.setId(UUID.randomUUID());
        when(userRepository.findByEmail("a@test.com")).thenReturn(Optional.of(user));
        when(userRepository.findByEmail("b@test.com")).thenReturn(Optional.empty());
        when(tokens.issue(any(), any())).thenReturn("t");

        String knownMsg = service.initiate(new PasswordResetRequest("a@test.com", null, null)).message();
        String unknownMsg = service.initiate(new PasswordResetRequest("b@test.com", null, null)).message();

        assertThat(knownMsg).isEqualTo(unknownMsg);
    }

    @Test
    void complete_validToken_changesPasswordRevokesSessionsAndUnlocks() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setPasswordHash("old");
        user.lock(LocalDateTime.now().plusHours(1));
        VerificationToken token = VerificationToken.create(user.getId(), "d", VerificationToken.PURPOSE_PASSWORD_RESET,
                NOW.minusSeconds(10), NOW.plusSeconds(3000));
        when(tokens.redeem("reset-token", VerificationToken.PURPOSE_PASSWORD_RESET)).thenReturn(token);
        when(userRepository.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("new-password-1")).thenReturn("new-hash");
        when(userRepository.save(any(User.class))).thenReturn(user);

        service.complete(new CompletePasswordResetRequest("reset-token", "new-password-1", "new-password-1", "ip", "ua"));

        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getFailedLoginAttempts()).isZero();
        verify(jwtProvider).revokeRefreshTokens(user.getId());

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditTrailPort, times(2)).record(captor.capture());
        List<AuditEventType> types = captor.getAllValues().stream().map(AuditEvent::getEventType).toList();
        assertThat(types).containsExactly(AuditEventType.PASSWORD_RESET_COMPLETED, AuditEventType.SESSIONS_REVOKED);
    }

    @Test
    void complete_refusedToken_recordsReasonAndChangesNothing() {
        when(tokens.redeem("used", VerificationToken.PURPOSE_PASSWORD_RESET)).thenThrow(new InvalidTokenException("used"));

        assertThatThrownBy(() -> service.complete(new CompletePasswordResetRequest("used", "new-password-1", "new-password-1", null, null)))
                .isInstanceOf(InvalidTokenException.class);

        verify(userRepository, never()).save(any());
        verify(jwtProvider, never()).revokeRefreshTokens(any());
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditTrailPort).record(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo(AuditEventType.PASSWORD_RESET_COMPLETED);
        assertThat(captor.getValue().getResult()).isEqualTo(AuditResult.FAILED);
        assertThat(captor.getValue().getReason()).isEqualTo("token used");
    }

    @Test
    void complete_mismatchedConfirmation_isRejectedBeforeTheTokenIsTouched() {
        assertThatThrownBy(() -> service.complete(new CompletePasswordResetRequest("t", "new-password-1", "other", null, null)))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(tokens, userRepository, jwtProvider);
    }
}
