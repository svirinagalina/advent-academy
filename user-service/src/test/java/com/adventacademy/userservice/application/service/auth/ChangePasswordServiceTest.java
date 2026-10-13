package com.adventacademy.userservice.application.service.auth;

import com.adventacademy.userservice.application.dto.request.ChangePasswordRequest;
import com.adventacademy.userservice.application.port.AuditTrailPort;
import com.adventacademy.userservice.application.port.JwtProvider;
import com.adventacademy.userservice.application.port.PasswordEncoder;
import com.adventacademy.userservice.domain.exception.InvalidCredentialsException;
import com.adventacademy.userservice.domain.model.AuditEvent;
import com.adventacademy.userservice.domain.model.AuditEventType;
import com.adventacademy.userservice.domain.model.AuditResult;
import com.adventacademy.userservice.domain.model.User;
import com.adventacademy.userservice.domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChangePasswordServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtProvider jwtProvider;
    @Mock private AuditTrailPort auditTrailPort;

    private ChangePasswordService service;
    private final Clock clock = Clock.fixed(Instant.parse("2026-05-28T10:00:00Z"), ZoneId.of("UTC"));

    private UUID userId;
    private User user;

    @BeforeEach
    void setUp() {
        service = new ChangePasswordService(userRepository, passwordEncoder, jwtProvider, auditTrailPort, clock);
        userId = UUID.randomUUID();
        user = new User();
        user.setId(userId);
        user.setPasswordHash("old-hash");
    }

    @Test
    void changePassword_correctOldPassword_recordsSuccessEvent() {
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("old", "old-hash")).thenReturn(true);
        when(passwordEncoder.encode("newpassword")).thenReturn("new-hash");
        when(userRepository.save(any(User.class))).thenReturn(user);

        service.changePassword(userId, new ChangePasswordRequest(
                "old", "newpassword", "10.0.0.3", "Safari"));

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditTrailPort, times(2)).record(captor.capture());
        AuditEvent event = captor.getAllValues().get(0);

        assertThat(event.getEventType()).isEqualTo(AuditEventType.PASSWORD_CHANGED);
        assertThat(event.getResult()).isEqualTo(AuditResult.SUCCESS);
        assertThat(event.getActorUserId()).isEqualTo(userId);
        assertThat(event.getTargetUserId()).isEqualTo(userId);
        assertThat(event.getIpAddress()).isEqualTo("10.0.0.3");

        assertThat(captor.getAllValues().get(1).getEventType()).isEqualTo(AuditEventType.SESSIONS_REVOKED);
    }

    @Test
    void changePassword_correctOldPassword_revokesEveryRefreshTokenOfTheUser() {
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("old", "old-hash")).thenReturn(true);
        when(passwordEncoder.encode("newpassword")).thenReturn("new-hash");
        when(userRepository.save(any(User.class))).thenReturn(user);

        service.changePassword(userId, new ChangePasswordRequest("old", "newpassword", null, null));

        verify(jwtProvider).revokeRefreshTokens(userId);
    }

    @Test
    void changePassword_wrongOldPassword_recordsFailedEventAndThrows() {
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "old-hash")).thenReturn(false);

        assertThatThrownBy(() -> service.changePassword(userId, new ChangePasswordRequest(
                "wrong", "newpassword", "10.0.0.3", "Safari")))
                .isInstanceOf(InvalidCredentialsException.class);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditTrailPort).record(captor.capture());
        AuditEvent event = captor.getValue();

        assertThat(event.getEventType()).isEqualTo(AuditEventType.PASSWORD_CHANGED);
        assertThat(event.getResult()).isEqualTo(AuditResult.FAILED);
        assertThat(event.getReason()).isEqualTo("Old password is incorrect");
    }
}
