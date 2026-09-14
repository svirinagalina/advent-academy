package com.adventacademy.userservice.application.service.auth;

import com.adventacademy.userservice.application.dto.request.RegisterUserRequest;
import com.adventacademy.userservice.application.port.AuditTrailPort;
import com.adventacademy.userservice.application.port.PasswordEncoder;
import com.adventacademy.userservice.domain.model.AuditEvent;
import com.adventacademy.userservice.domain.model.AuditEventType;
import com.adventacademy.userservice.domain.model.AuditResult;
import com.adventacademy.userservice.domain.model.User;
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
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegisterUserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private VerificationTokenService tokens;
    @Mock private EmailDispatcher emails;
    @Mock private AuditTrailPort auditTrailPort;

    private RegisterUserService service;
    private final Clock clock = Clock.fixed(Instant.parse("2026-05-28T10:00:00Z"), ZoneId.of("UTC"));

    @BeforeEach
    void setUp() {
        service = new RegisterUserService(userRepository, passwordEncoder, tokens, emails, auditTrailPort, clock);
    }

    @Test
    void register_newUser_recordsUserRegisteredEvent() {
        UUID savedId = UUID.randomUUID();
        when(userRepository.existsByEmail("new@test.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(savedId);
            return u;
        });

        service.register(new RegisterUserRequest(
                "new@test.com", "password123", "John", "Doe", "10.0.0.1", "Mozilla"));

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditTrailPort).record(captor.capture());
        AuditEvent event = captor.getValue();

        assertThat(event.getEventType()).isEqualTo(AuditEventType.USER_REGISTERED);
        assertThat(event.getResult()).isEqualTo(AuditResult.SUCCESS);
        assertThat(event.getActorUserId()).isEqualTo(savedId);
        assertThat(event.getTargetUserId()).isEqualTo(savedId);
        assertThat(event.getIpAddress()).isEqualTo("10.0.0.1");
        assertThat(event.getUserAgent()).isEqualTo("Mozilla");
    }

    @Test
    void register_newUser_issuesDigestOnlyTokenAndMailsIt_neverStoringPlaintextOnTheUser() {
        UUID savedId = UUID.randomUUID();
        when(userRepository.existsByEmail("new@test.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(savedId);
            return u;
        });
        when(tokens.issue(savedId, VerificationToken.PURPOSE_EMAIL_VERIFICATION)).thenReturn("plain-token");

        service.register(new RegisterUserRequest("new@test.com", "password123", "John", "Doe", null, null));

        verify(emails).sendEmailVerification(savedId, "new@test.com", "plain-token");
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getEmailVerificationToken()).isNull();
        assertThat(saved.getValue().isEmailVerified()).isFalse();
    }
}
