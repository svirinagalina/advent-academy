package com.adventacademy.userservice.application.service.auth;

import com.adventacademy.userservice.application.dto.request.PasswordResetRequest;
import com.adventacademy.userservice.application.port.AuditTrailPort;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private AuditTrailPort auditTrailPort;

    private PasswordResetService service;
    private final Clock clock = Clock.fixed(Instant.parse("2026-05-28T10:00:00Z"), ZoneId.of("UTC"));

    @BeforeEach
    void setUp() {
        service = new PasswordResetService(userRepository, auditTrailPort, clock);
    }

    @Test
    void initiate_knownEmail_recordsSuccessfulEventWithActorAndTarget() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("known@test.com");
        when(userRepository.findByEmail("known@test.com")).thenReturn(Optional.of(user));

        service.initiate(new PasswordResetRequest("known@test.com", "10.0.0.1", "Mozilla"));

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditTrailPort).record(captor.capture());
        AuditEvent event = captor.getValue();

        assertThat(event.getEventType()).isEqualTo(AuditEventType.PASSWORD_RESET_INITIATED);
        assertThat(event.getResult()).isEqualTo(AuditResult.SUCCESS);
        assertThat(event.getActorUserId()).isEqualTo(user.getId());
        assertThat(event.getTargetUserId()).isEqualTo(user.getId());
        assertThat(event.getIpAddress()).isEqualTo("10.0.0.1");
        assertThat(event.getUserAgent()).isEqualTo("Mozilla");
    }

    @Test
    void initiate_unknownEmail_recordsFailedEventWithReason() {
        when(userRepository.findByEmail("ghost@test.com")).thenReturn(Optional.empty());

        service.initiate(new PasswordResetRequest("ghost@test.com", "10.0.0.1", "curl/8"));

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditTrailPort).record(captor.capture());
        AuditEvent event = captor.getValue();

        assertThat(event.getEventType()).isEqualTo(AuditEventType.PASSWORD_RESET_INITIATED);
        assertThat(event.getResult()).isEqualTo(AuditResult.FAILED);
        assertThat(event.getReason()).isEqualTo("Unknown email");
        assertThat(event.getActorUserId()).isNull();
        assertThat(event.getTargetUserId()).isNull();
        assertThat(event.getIpAddress()).isEqualTo("10.0.0.1");
    }

    @Test
    void initiate_responseIsIdenticalForKnownAndUnknownEmail() {
        when(userRepository.findByEmail("a@test.com")).thenReturn(Optional.of(new User()));
        when(userRepository.findByEmail("b@test.com")).thenReturn(Optional.empty());

        String knownMsg = service.initiate(new PasswordResetRequest("a@test.com", null, null)).message();
        String unknownMsg = service.initiate(new PasswordResetRequest("b@test.com", null, null)).message();

        assertThat(knownMsg).isEqualTo(unknownMsg);
    }
}
