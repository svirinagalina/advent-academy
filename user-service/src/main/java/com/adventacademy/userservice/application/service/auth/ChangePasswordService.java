package com.adventacademy.userservice.application.service.auth;

import com.adventacademy.userservice.application.dto.request.ChangePasswordRequest;
import com.adventacademy.userservice.application.dto.response.MessageResponse;
import com.adventacademy.userservice.application.port.AuditTrailPort;
import com.adventacademy.userservice.application.port.PasswordEncoder;
import com.adventacademy.userservice.domain.exception.InvalidCredentialsException;
import com.adventacademy.userservice.domain.exception.UserNotFoundException;
import com.adventacademy.userservice.domain.model.AuditEvent;
import com.adventacademy.userservice.domain.model.AuditEventType;
import com.adventacademy.userservice.domain.model.AuditResult;
import com.adventacademy.userservice.domain.model.User;
import com.adventacademy.userservice.domain.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class ChangePasswordService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditTrailPort auditTrailPort;
    private final SessionRevocationService sessionRevocationService;
    private final Clock clock;

    public ChangePasswordService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuditTrailPort auditTrailPort,
            SessionRevocationService sessionRevocationService,
            Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditTrailPort = auditTrailPort;
        this.sessionRevocationService = sessionRevocationService;
        this.clock = clock;
    }

    @Transactional
    public MessageResponse changePassword(UUID userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (!passwordEncoder.matches(request.oldPassword(), user.getPasswordHash())) {
            auditTrailPort.record(new AuditEvent(
                    UUID.randomUUID(),
                    AuditEventType.PASSWORD_CHANGED,
                    user.getId(),
                    user.getId(),
                    LocalDateTime.now(clock),
                    request.ipAddress(),
                    request.userAgent(),
                    AuditResult.FAILED,
                    "Old password is incorrect",
                    null
            ));
            throw new InvalidCredentialsException("Old password is incorrect");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        // Revoke all sessions when password changes
        sessionRevocationService.revokeSessionsOnPasswordChange(user.getId());

        auditTrailPort.record(new AuditEvent(
                UUID.randomUUID(),
                AuditEventType.PASSWORD_CHANGED,
                user.getId(),
                user.getId(),
                LocalDateTime.now(clock),
                request.ipAddress(),
                request.userAgent(),
                AuditResult.SUCCESS,
                null,
                null
        ));

        return new MessageResponse("Password changed successfully");
    }
}