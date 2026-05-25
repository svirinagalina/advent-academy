package com.adventacademy.userservice.application.service.auth;

import com.adventacademy.userservice.application.dto.response.MessageResponse;
import com.adventacademy.userservice.application.port.AuditTrailPort;
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
public class ConfirmEmailService {

    private final UserRepository userRepository;
    private final AuditTrailPort auditTrailPort;
    private final Clock clock;

    public ConfirmEmailService(
            UserRepository userRepository,
            AuditTrailPort auditTrailPort,
            Clock clock) {
        this.userRepository = userRepository;
        this.auditTrailPort = auditTrailPort;
        this.clock = clock;
    }

    @Transactional
    public MessageResponse confirmEmail(String token, String ipAddress, String userAgent) {
        User user = userRepository.findByEmailVerificationToken(token)
                .orElseThrow(() -> new UserNotFoundException("Invalid verification token"));

        user.setEmailVerified(true);
        user.activate();
        user.setEmailVerificationToken(null);
        userRepository.save(user);

        auditTrailPort.record(new AuditEvent(
                UUID.randomUUID(),
                AuditEventType.EMAIL_CONFIRMED,
                user.getId(),
                user.getId(),
                LocalDateTime.now(clock),
                ipAddress,
                userAgent,
                AuditResult.SUCCESS,
                null,
                null
        ));

        return new MessageResponse("Email confirmed successfully");
    }
}