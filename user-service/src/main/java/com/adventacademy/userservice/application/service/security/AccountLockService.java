package com.adventacademy.userservice.application.service.security;

import com.adventacademy.userservice.application.port.AuditTrailPort;
import com.adventacademy.userservice.domain.exception.UserNotFoundException;
import com.adventacademy.userservice.domain.model.AuditEvent;
import com.adventacademy.userservice.domain.model.AuditEventType;
import com.adventacademy.userservice.domain.model.AuditResult;
import com.adventacademy.userservice.domain.model.User;
import com.adventacademy.userservice.domain.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class AccountLockService {

    private final UserRepository userRepository;
    private final AuditTrailPort auditTrailPort;
    private final int maxAttempts;
    private final int lockDurationMinutes;
    private final Clock clock;

    public AccountLockService(
            UserRepository userRepository,
            AuditTrailPort auditTrailPort,
            @Value("${security.account-lock.max-failed-attempts:5}") int maxAttempts,
            @Value("${security.account-lock.lock-duration-minutes:30}") int lockDurationMinutes,
            Clock clock) {
        this.userRepository = userRepository;
        this.auditTrailPort = auditTrailPort;
        this.maxAttempts = maxAttempts;
        this.lockDurationMinutes = lockDurationMinutes;
        this.clock = clock;
    }

    @Transactional
    public void handleFailedLogin(User user) {
        user.incrementFailedAttempts();
        if (user.getFailedLoginAttempts() >= maxAttempts) {
            user.lock(LocalDateTime.now(clock).plusMinutes(lockDurationMinutes));
            auditTrailPort.record(new AuditEvent(
                    UUID.randomUUID(),
                    AuditEventType.USER_BLOCKED,
                    user.getId(),
                    user.getId(),
                    LocalDateTime.now(clock),
                    null,
                    null,
                    AuditResult.SUCCESS,
                    "Account locked after " + maxAttempts + " failed attempts",
                    null
            ));
        }
        userRepository.save(user);
    }

    @Transactional
    public void unlockAccount(UUID userId, String ipAddress, String userAgent) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        user.unlock();
        user.resetFailedAttempts();
        userRepository.save(user);

        auditTrailPort.record(new AuditEvent(
                UUID.randomUUID(),
                AuditEventType.USER_UNBLOCKED,
                userId,
                userId,
                LocalDateTime.now(clock),
                ipAddress,
                userAgent,
                AuditResult.SUCCESS,
                null,
                null
        ));
    }
}