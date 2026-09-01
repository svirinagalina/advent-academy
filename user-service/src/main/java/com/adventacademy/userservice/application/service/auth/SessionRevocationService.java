package com.adventacademy.userservice.application.service.auth;

import com.adventacademy.userservice.application.port.AuditTrailPort;
import com.adventacademy.userservice.domain.model.AuditEvent;
import com.adventacademy.userservice.domain.model.AuditEventType;
import com.adventacademy.userservice.domain.model.AuditResult;
import com.adventacademy.userservice.domain.model.RefreshSession;
import com.adventacademy.userservice.domain.repository.RefreshSessionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class SessionRevocationService {

    private final RefreshSessionRepository refreshSessionRepository;
    private final AuditTrailPort auditTrailPort;
    private final Clock clock;

    public SessionRevocationService(
            RefreshSessionRepository refreshSessionRepository,
            AuditTrailPort auditTrailPort,
            Clock clock) {
        this.refreshSessionRepository = refreshSessionRepository;
        this.auditTrailPort = auditTrailPort;
        this.clock = clock;
    }

    @Transactional
    public void revokeSessionsOnPasswordChange(UUID userId) {
        revokeAllSessionsForReason(userId, "Password changed", "PASSWORD_CHANGED");
    }

    @Transactional
    public void revokeSessionsOnPasswordReset(UUID userId) {
        revokeAllSessionsForReason(userId, "Password reset", "PASSWORD_RESET");
    }

    @Transactional
    public void revokeSessionsOnUserBlock(UUID userId) {
        revokeAllSessionsForReason(userId, "User account blocked", "ACCOUNT_BLOCKED");
    }

    @Transactional
    public void revokeSessionsOnRoleChange(UUID userId) {
        revokeAllSessionsForReason(userId, "User roles changed", "ROLE_CHANGED");
    }

    @Transactional
    public void revokeSessionsOnUserDelete(UUID userId) {
        revokeAllSessionsForReason(userId, "User account deleted", "USER_DELETED");
    }

    private void revokeAllSessionsForReason(UUID userId, String reason, String reasonCode) {
        List<RefreshSession> sessions = refreshSessionRepository.findAllByUserId(userId);

        int revokedCount = 0;
        for (RefreshSession session : sessions) {
            if (!session.getStatus().equals(RefreshSession.Status.REVOKED)) {
                session.revoke();
                refreshSessionRepository.save(session);
                revokedCount++;
            }
        }

        log.info("Revoked {} sessions for user {} due to: {}", revokedCount, userId, reason);

        auditTrailPort.record(new AuditEvent(
                UUID.randomUUID(),
                AuditEventType.SESSIONS_REVOKED,
                userId,
                userId,
                LocalDateTime.now(clock),
                null,
                null,
                AuditResult.SUCCESS,
                reason + " (" + revokedCount + " sessions revoked)",
                Map.of("reasonCode", reasonCode)
        ));
    }
}
