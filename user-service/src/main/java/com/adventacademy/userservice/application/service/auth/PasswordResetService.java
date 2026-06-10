package com.adventacademy.userservice.application.service.auth;

import com.adventacademy.userservice.application.dto.request.PasswordResetRequest;
import com.adventacademy.userservice.application.dto.response.MessageResponse;
import com.adventacademy.userservice.application.port.AuditTrailPort;
import com.adventacademy.userservice.domain.model.AuditEvent;
import com.adventacademy.userservice.domain.model.AuditEventType;
import com.adventacademy.userservice.domain.model.AuditResult;
import com.adventacademy.userservice.domain.model.User;
import com.adventacademy.userservice.domain.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Use case service for the password-reset initiation flow.
 * <p>
 * Records a {@link AuditEventType#PASSWORD_RESET_INITIATED} audit event for every
 * initiation attempt — successful when the email belongs to a known account, failed
 * otherwise. To avoid leaking which emails are registered, the public response is
 * identical in both cases.
 * </p>
 * <p>
 * The actual delivery channel (e-mail with a reset link) is intentionally out of
 * scope for this service; it focuses on the security-audit obligation defined by
 * the audit trail requirement.
 * </p>
 */
@Service
public class PasswordResetService {

    private final UserRepository userRepository;
    private final AuditTrailPort auditTrailPort;
    private final Clock clock;

    public PasswordResetService(
            UserRepository userRepository,
            AuditTrailPort auditTrailPort,
            Clock clock) {
        this.userRepository = userRepository;
        this.auditTrailPort = auditTrailPort;
        this.clock = clock;
    }

    @Transactional
    public MessageResponse initiate(PasswordResetRequest request) {
        Optional<User> maybeUser = userRepository.findByEmail(request.email());

        if (maybeUser.isPresent()) {
            User user = maybeUser.get();
            auditTrailPort.record(new AuditEvent(
                    UUID.randomUUID(),
                    AuditEventType.PASSWORD_RESET_INITIATED,
                    user.getId(),
                    user.getId(),
                    LocalDateTime.now(clock),
                    request.ipAddress(),
                    request.userAgent(),
                    AuditResult.SUCCESS,
                    null,
                    null
            ));
        } else {
            auditTrailPort.record(new AuditEvent(
                    UUID.randomUUID(),
                    AuditEventType.PASSWORD_RESET_INITIATED,
                    null,
                    null,
                    LocalDateTime.now(clock),
                    request.ipAddress(),
                    request.userAgent(),
                    AuditResult.FAILED,
                    "Unknown email",
                    null
            ));
        }

        return new MessageResponse("If the email exists, a reset link has been sent.");
    }
}
