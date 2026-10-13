package com.adventacademy.userservice.application.service.auth;

import com.adventacademy.userservice.application.port.AuditTrailPort;
import com.adventacademy.userservice.application.port.EmailService;
import com.adventacademy.userservice.domain.model.AuditEvent;
import com.adventacademy.userservice.domain.model.AuditEventType;
import com.adventacademy.userservice.domain.model.AuditResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Delivery policy for transactional e-mail (verification links, reset links, recovery codes).
 * <p>
 * The message is handed to {@link EmailService} only after the surrounding transaction has
 * committed, so a rolled-back registration never produces a link to a non-existent token. A
 * delivery failure does not fail the request: the token stays valid, the user can ask for it again
 * through the public re-send endpoints, and the failure is recorded as an
 * {@link AuditEventType#EMAIL_DELIVERY_FAILED} audit event without the token itself.
 * See {@code docs/security/production-security-defaults.md}, "Transactional e-mail".
 */
@Component
public class EmailDispatcher {

    private static final Logger log = LoggerFactory.getLogger(EmailDispatcher.class);

    private final EmailService emailService;
    private final AuditTrailPort auditTrail;
    private final Clock clock;

    public EmailDispatcher(EmailService emailService, AuditTrailPort auditTrail, Clock clock) {
        this.emailService = emailService;
        this.auditTrail = auditTrail;
        this.clock = clock;
    }

    public void sendEmailVerification(UUID userId, String email, String token) {
        afterCommit(userId, "email_verification", () -> emailService.sendEmailVerification(email, token));
    }

    public void sendPasswordReset(UUID userId, String email, String token) {
        afterCommit(userId, "password_reset", () -> emailService.sendPasswordReset(email, token));
    }

    private void afterCommit(UUID userId, String kind, Runnable delivery) {
        Runnable guarded = () -> deliver(userId, kind, delivery);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    guarded.run();
                }
            });
        } else {
            guarded.run();
        }
    }

    private void deliver(UUID userId, String kind, Runnable delivery) {
        try {
            delivery.run();
        } catch (RuntimeException e) {
            // The exception message may quote the recipient or the SMTP dialogue; the token is
            // never part of it, but keep the log line to the class name to be safe.
            log.warn("E-mail delivery failed: kind={} user={} cause={}", kind, userId, e.getClass().getSimpleName());
            auditTrail.record(new AuditEvent(
                    UUID.randomUUID(),
                    AuditEventType.EMAIL_DELIVERY_FAILED,
                    userId,
                    userId,
                    LocalDateTime.now(clock),
                    null,
                    null,
                    AuditResult.FAILED,
                    kind + ": " + e.getClass().getSimpleName(),
                    null
            ));
        }
    }
}
