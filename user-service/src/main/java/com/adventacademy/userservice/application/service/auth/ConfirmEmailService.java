package com.adventacademy.userservice.application.service.auth;

import com.adventacademy.userservice.application.dto.response.MessageResponse;
import com.adventacademy.userservice.application.port.AuditTrailPort;
import com.adventacademy.userservice.domain.exception.InvalidTokenException;
import com.adventacademy.userservice.domain.exception.UserNotFoundException;
import com.adventacademy.userservice.domain.model.AuditEvent;
import com.adventacademy.userservice.domain.model.AuditEventType;
import com.adventacademy.userservice.domain.model.AuditResult;
import com.adventacademy.userservice.domain.model.User;
import com.adventacademy.userservice.domain.model.UserStatus;
import com.adventacademy.userservice.domain.model.VerificationToken;
import com.adventacademy.userservice.domain.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Use case service for email address verification.
 * <p>
 * Redeems the one-time verification token (see {@link VerificationTokenService}), marks the email
 * as verified and activates the account. Re-sending a verification e-mail issues a fresh token and
 * answers identically whether or not the address is known, so the endpoint cannot be used to
 * enumerate accounts.
 */
@Service
public class ConfirmEmailService {

    static final String RESEND_MESSAGE = "If an unverified account exists for this email, a new verification link has been sent.";

    private final UserRepository userRepository;
    private final VerificationTokenService tokens;
    private final EmailDispatcher emails;
    private final AuditTrailPort auditTrailPort;
    private final Clock clock;

    public ConfirmEmailService(
            UserRepository userRepository,
            VerificationTokenService tokens,
            EmailDispatcher emails,
            AuditTrailPort auditTrailPort,
            Clock clock) {
        this.userRepository = userRepository;
        this.tokens = tokens;
        this.emails = emails;
        this.auditTrailPort = auditTrailPort;
        this.clock = clock;
    }

    @Transactional
    public MessageResponse confirmEmail(String token, String ipAddress, String userAgent) {
        VerificationToken redeemed;
        try {
            redeemed = tokens.redeem(token, VerificationToken.PURPOSE_EMAIL_VERIFICATION);
        } catch (InvalidTokenException e) {
            auditTrailPort.record(event(AuditEventType.EMAIL_CONFIRMED, null, ipAddress, userAgent,
                    AuditResult.FAILED, "token " + e.getReason()));
            throw e;
        }

        User user = userRepository.findByIdForUpdate(redeemed.getUserId())
                .orElseThrow(() -> new UserNotFoundException(redeemed.getUserId()));

        user.setEmailVerified(true);
        if (user.getStatus() == UserStatus.PENDING) {
            user.activate();
        }
        userRepository.save(user);

        auditTrailPort.record(event(AuditEventType.EMAIL_CONFIRMED, user.getId(), ipAddress, userAgent,
                AuditResult.SUCCESS, null));

        return new MessageResponse("Email confirmed successfully");
    }

    /** Issues a new verification token for a pending account; silent for unknown or verified addresses. */
    @Transactional
    public MessageResponse resendVerification(String email, String ipAddress, String userAgent) {
        userRepository.findByEmail(email)
                .filter(user -> !user.isEmailVerified())
                .ifPresentOrElse(user -> {
                    String token = tokens.issue(user.getId(), VerificationToken.PURPOSE_EMAIL_VERIFICATION);
                    emails.sendEmailVerification(user.getId(), user.getEmail(), token);
                    auditTrailPort.record(event(AuditEventType.EMAIL_VERIFICATION_RESENT, user.getId(), ipAddress, userAgent,
                            AuditResult.SUCCESS, null));
                }, () -> auditTrailPort.record(event(AuditEventType.EMAIL_VERIFICATION_RESENT, null, ipAddress, userAgent,
                        AuditResult.FAILED, "Unknown or already verified email")));

        return new MessageResponse(RESEND_MESSAGE);
    }

    private AuditEvent event(AuditEventType type, UUID userId, String ip, String userAgent, AuditResult result, String reason) {
        return new AuditEvent(UUID.randomUUID(), type, userId, userId, LocalDateTime.now(clock), ip, userAgent, result, reason, null);
    }
}
