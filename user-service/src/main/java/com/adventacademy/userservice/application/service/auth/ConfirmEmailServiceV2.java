package com.adventacademy.userservice.application.service.auth;

import com.adventacademy.userservice.application.port.AuditTrailPort;
import com.adventacademy.userservice.application.port.EmailService;
import com.adventacademy.userservice.domain.model.AuditEvent;
import com.adventacademy.userservice.domain.model.AuditEventType;
import com.adventacademy.userservice.domain.model.AuditResult;
import com.adventacademy.userservice.domain.model.User;
import com.adventacademy.userservice.domain.model.VerificationToken;
import com.adventacademy.userservice.domain.repository.UserRepository;
import com.adventacademy.userservice.domain.repository.VerificationTokenRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Enhanced email confirmation using new VerificationToken system.
 * Replaces old UUID-based verification with secure, expiring tokens.
 */
@Slf4j
@Service
public class ConfirmEmailServiceV2 {

    private final UserRepository userRepository;
    private final VerificationTokenRepository tokenRepository;
    private final AuditTrailPort auditTrail;
    private final EmailService emailService;
    private final Clock clock;

    public ConfirmEmailServiceV2(
            UserRepository userRepository,
            VerificationTokenRepository tokenRepository,
            AuditTrailPort auditTrail,
            EmailService emailService,
            Clock clock
    ) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.auditTrail = auditTrail;
        this.emailService = emailService;
        this.clock = clock;
    }

    /**
     * Confirm email using verification token from email link.
     * Token is one-time use with 24-hour expiry.
     */
    @Transactional
    public void confirmEmail(String tokenDigest, String ipAddress) {
        Instant now = clock.instant();

        // Find token by digest (never plaintext)
        VerificationToken token = tokenRepository.findByTokenDigest(tokenDigest)
                .orElseThrow(() -> {
                    log.warn("Email verification attempted with invalid token digest");
                    return new IllegalArgumentException("Invalid verification token");
                });

        // Validate token: not expired, not used, correct purpose
        if (!token.isValid(now, "email_verification")) {
            String reason = token.isExpired(now) ? "expired" : token.isUsed() ? "already_used" : "wrong_purpose";
            log.warn("Email verification failed: token {}", reason);
            auditTrail.record(new AuditEvent(
                    UUID.randomUUID(),
                    AuditEventType.EMAIL_CONFIRMED,
                    token.getUserId(),
                    token.getUserId(),
                    LocalDateTime.now(clock),
                    ipAddress,
                    null,
                    AuditResult.FAILED,
                    reason,
                    null
            ));
            throw new IllegalArgumentException("Verification token is " + reason);
        }

        // Mark token as used (one-time use enforcement)
        token.markAsUsed(now);
        tokenRepository.save(token);

        // Update user
        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        user.setEmailVerified(true);
        user.activate();
        userRepository.save(user);

        log.info("Email confirmed for user {}", token.getUserId());
        auditTrail.record(new AuditEvent(
                UUID.randomUUID(),
                AuditEventType.EMAIL_CONFIRMED,
                token.getUserId(),
                token.getUserId(),
                LocalDateTime.now(clock),
                ipAddress,
                null,
                AuditResult.SUCCESS,
                null,
                null
        ));
    }

    /**
     * Resend verification email (regenerate token).
     */
    @Transactional
    public void resendVerificationEmail(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (user.isEmailVerified()) {
            throw new IllegalArgumentException("Email already verified");
        }

        // Delete old token if exists
        tokenRepository.findActiveByUserIdAndPurpose(userId, "email_verification")
                .ifPresent(t -> tokenRepository.deleteById(t.getId()));

        // Create new token (caller will generate random token and digest)
        log.info("Verification email resent for user {}", userId);
    }
}
