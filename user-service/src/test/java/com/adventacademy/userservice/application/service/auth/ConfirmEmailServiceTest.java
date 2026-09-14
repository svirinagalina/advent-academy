package com.adventacademy.userservice.application.service.auth;

import com.adventacademy.userservice.application.port.AuditTrailPort;
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
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfirmEmailServiceTest {

    private static final Instant NOW = Instant.parse("2026-05-28T10:00:00Z");

    @Mock private UserRepository userRepository;
    @Mock private VerificationTokenService tokens;
    @Mock private EmailDispatcher emails;
    @Mock private AuditTrailPort auditTrailPort;

    private ConfirmEmailService service;
    private final Clock clock = Clock.fixed(NOW, ZoneId.of("UTC"));

    @BeforeEach
    void setUp() {
        service = new ConfirmEmailService(userRepository, tokens, emails, auditTrailPort, clock);
    }

    @Test
    void confirmEmail_validToken_activatesUserAndRecordsEmailConfirmedEvent() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setStatus(UserStatus.PENDING);
        VerificationToken token = VerificationToken.create(user.getId(), "digest", VerificationToken.PURPOSE_EMAIL_VERIFICATION,
                NOW.minusSeconds(60), NOW.plusSeconds(3600));
        when(tokens.redeem("tok-1", VerificationToken.PURPOSE_EMAIL_VERIFICATION)).thenReturn(token);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);

        service.confirmEmail("tok-1", "10.0.0.2", "Firefox");

        assertThat(user.isEmailVerified()).isTrue();
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditTrailPort).record(captor.capture());
        AuditEvent event = captor.getValue();
        assertThat(event.getEventType()).isEqualTo(AuditEventType.EMAIL_CONFIRMED);
        assertThat(event.getResult()).isEqualTo(AuditResult.SUCCESS);
        assertThat(event.getActorUserId()).isEqualTo(user.getId());
        assertThat(event.getIpAddress()).isEqualTo("10.0.0.2");
        assertThat(event.getUserAgent()).isEqualTo("Firefox");
    }

    @Test
    void confirmEmail_refusedToken_recordsFailureWithReasonAndRethrows() {
        when(tokens.redeem("bad", VerificationToken.PURPOSE_EMAIL_VERIFICATION)).thenThrow(new InvalidTokenException("expired"));

        assertThatThrownBy(() -> service.confirmEmail("bad", "ip", "ua"))
                .isInstanceOf(InvalidTokenException.class);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditTrailPort).record(captor.capture());
        assertThat(captor.getValue().getResult()).isEqualTo(AuditResult.FAILED);
        assertThat(captor.getValue().getReason()).isEqualTo("token expired");
        verify(userRepository, never()).save(any());
    }

    @Test
    void resendVerification_pendingAccount_issuesFreshTokenAndSendsMail() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("pending@test.com");
        when(userRepository.findByEmail("pending@test.com")).thenReturn(Optional.of(user));
        when(tokens.issue(user.getId(), VerificationToken.PURPOSE_EMAIL_VERIFICATION)).thenReturn("fresh");

        service.resendVerification("pending@test.com", "ip", "ua");

        verify(emails).sendEmailVerification(user.getId(), "pending@test.com", "fresh");
    }

    @Test
    void resendVerification_answersTheSameForUnknownVerifiedAndPendingAddresses() {
        User verified = new User();
        verified.setId(UUID.randomUUID());
        verified.setEmailVerified(true);
        User pending = new User();
        pending.setId(UUID.randomUUID());
        when(userRepository.findByEmail("ghost@test.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("done@test.com")).thenReturn(Optional.of(verified));
        when(userRepository.findByEmail("pending@test.com")).thenReturn(Optional.of(pending));
        when(tokens.issue(pending.getId(), VerificationToken.PURPOSE_EMAIL_VERIFICATION)).thenReturn("fresh");

        String unknown = service.resendVerification("ghost@test.com", null, null).message();
        String done = service.resendVerification("done@test.com", null, null).message();
        String fresh = service.resendVerification("pending@test.com", null, null).message();

        assertThat(unknown).isEqualTo(done).isEqualTo(fresh);
        // Only the pending account gets a token and a mail; the other two answers are pure theatre.
        verify(emails).sendEmailVerification(pending.getId(), null, "fresh");
        verify(tokens, never()).issue(verified.getId(), VerificationToken.PURPOSE_EMAIL_VERIFICATION);
    }
}
