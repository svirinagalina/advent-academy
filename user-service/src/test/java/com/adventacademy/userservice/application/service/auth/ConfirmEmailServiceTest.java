package com.adventacademy.userservice.application.service.auth;

import com.adventacademy.userservice.application.port.AuditTrailPort;
import com.adventacademy.userservice.domain.exception.UserNotFoundException;
import com.adventacademy.userservice.domain.model.AuditEvent;
import com.adventacademy.userservice.domain.model.AuditEventType;
import com.adventacademy.userservice.domain.model.AuditResult;
import com.adventacademy.userservice.domain.model.User;
import com.adventacademy.userservice.domain.model.UserStatus;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfirmEmailServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private AuditTrailPort auditTrailPort;

    private ConfirmEmailService service;
    private final Clock clock = Clock.fixed(Instant.parse("2026-05-28T10:00:00Z"), ZoneId.of("UTC"));

    @BeforeEach
    void setUp() {
        service = new ConfirmEmailService(userRepository, auditTrailPort, clock);
    }

    @Test
    void confirmEmail_validToken_recordsEmailConfirmedEvent() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setStatus(UserStatus.PENDING);
        user.setEmailVerificationToken("tok-1");
        when(userRepository.findByEmailVerificationToken("tok-1")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);

        service.confirmEmail("tok-1", "10.0.0.2", "Firefox");

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditTrailPort).record(captor.capture());
        AuditEvent event = captor.getValue();

        assertThat(event.getEventType()).isEqualTo(AuditEventType.EMAIL_CONFIRMED);
        assertThat(event.getResult()).isEqualTo(AuditResult.SUCCESS);
        assertThat(event.getActorUserId()).isEqualTo(user.getId());
        assertThat(event.getTargetUserId()).isEqualTo(user.getId());
        assertThat(event.getIpAddress()).isEqualTo("10.0.0.2");
        assertThat(event.getUserAgent()).isEqualTo("Firefox");
    }

    @Test
    void confirmEmail_invalidToken_throwsAndDoesNotRecord() {
        when(userRepository.findByEmailVerificationToken("bad")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirmEmail("bad", "ip", "ua"))
                .isInstanceOf(UserNotFoundException.class);

        verifyNoInteractions(auditTrailPort);
    }
}
