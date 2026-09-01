package com.adventacademy.userservice.application.service.auth;

import com.adventacademy.userservice.application.port.AuditTrailPort;
import com.adventacademy.userservice.domain.model.AuditEvent;
import com.adventacademy.userservice.domain.model.AuditEventType;
import com.adventacademy.userservice.domain.model.AuditResult;
import com.adventacademy.userservice.domain.model.RefreshSession;
import com.adventacademy.userservice.domain.repository.RefreshSessionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class SessionLifecycleService {

    private final RefreshSessionRepository refreshSessionRepository;
    private final AuditTrailPort auditTrailPort;
    private final long idleTimeoutSeconds;
    private final long sessionRetentionDays;
    private final Clock clock;

    public SessionLifecycleService(
            RefreshSessionRepository refreshSessionRepository,
            AuditTrailPort auditTrailPort,
            @Value("${security.refresh-session.idle-timeout-minutes:20}") long idleTimeoutMinutes,
            @Value("${security.refresh-session.session-retention-days:90}") long sessionRetentionDays,
            Clock clock) {
        this.refreshSessionRepository = refreshSessionRepository;
        this.auditTrailPort = auditTrailPort;
        this.idleTimeoutSeconds = idleTimeoutMinutes * 60;
        this.sessionRetentionDays = sessionRetentionDays;
        this.clock = clock;
    }

    @Transactional
    public void logoutCurrentSession(UUID userId, String jtiDigest, String userAgent, String ipAddress) {
        RefreshSession session = refreshSessionRepository.findByJtiDigest(jtiDigest)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));

        if (!session.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Cannot logout another user's session");
        }

        session.revoke();
        refreshSessionRepository.save(session);

        log.info("Session logged out for user: {}", userId);

        auditTrailPort.record(new AuditEvent(
                UUID.randomUUID(),
                AuditEventType.LOGOUT,
                userId,
                userId,
                LocalDateTime.now(clock),
                ipAddress,
                userAgent,
                AuditResult.SUCCESS,
                "Current session logged out",
                null
        ));
    }

    @Transactional
    public void logoutAllSessions(UUID userId, String userAgent, String ipAddress) {
        List<RefreshSession> sessions = refreshSessionRepository.findAllByUserId(userId);

        int revokedCount = 0;
        for (RefreshSession session : sessions) {
            if (!session.getStatus().equals(RefreshSession.Status.REVOKED)) {
                session.revoke();
                refreshSessionRepository.save(session);
                revokedCount++;
            }
        }

        log.info("All {} sessions logged out for user: {}", revokedCount, userId);

        auditTrailPort.record(new AuditEvent(
                UUID.randomUUID(),
                AuditEventType.LOGOUT_ALL,
                userId,
                userId,
                LocalDateTime.now(clock),
                ipAddress,
                userAgent,
                AuditResult.SUCCESS,
                "All sessions logged out (" + revokedCount + " revoked)",
                null
        ));
    }

    @Transactional
    public void revokeTokenFamily(UUID tokenFamilyId, String reason, String userAgent, String ipAddress) {
        List<RefreshSession> sessions = refreshSessionRepository.findByTokenFamilyId(tokenFamilyId);

        int revokedCount = 0;
        UUID userId = null;
        for (RefreshSession session : sessions) {
            userId = session.getUserId();
            if (!session.getStatus().equals(RefreshSession.Status.REVOKED)) {
                session.revoke();
                refreshSessionRepository.save(session);
                revokedCount++;
            }
        }

        if (userId != null) {
            log.info("Token family {} revoked for user: {} ({} sessions)", tokenFamilyId, userId, revokedCount);

            auditTrailPort.record(new AuditEvent(
                    UUID.randomUUID(),
                    AuditEventType.SECURITY_INCIDENT,
                    userId,
                    userId,
                    LocalDateTime.now(clock),
                    ipAddress,
                    userAgent,
                    AuditResult.SUCCESS,
                    "Token family revoked: " + reason,
                    null
            ));
        }
    }

    @Scheduled(fixedRateString = "${security.session.cleanup-interval-minutes:60}m")
    @Transactional
    public void cleanupExpiredSessions() {
        Instant now = clock.instant();

        // Find sessions that expired
        List<RefreshSession> expiredSessions = refreshSessionRepository.findExpiredSessions(now);

        log.info("Cleaning up {} expired sessions", expiredSessions.size());

        // Delete sessions older than retention period
        Instant cutoffTime = now.minusSeconds(sessionRetentionDays * 86400);
        for (RefreshSession session : expiredSessions) {
            if (session.getCreatedAt().isBefore(cutoffTime)) {
                refreshSessionRepository.deleteById(session.getId());
                log.debug("Deleted expired session: {}", session.getId());
            }
        }
    }

    @Transactional
    public void checkAndRevokeIdleSession(UUID userId, String jtiDigest) {
        RefreshSession session = refreshSessionRepository.findByJtiDigest(jtiDigest)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));

        if (!session.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Cannot revoke another user's session");
        }

        Instant now = clock.instant();
        if (session.isIdleExpired(now, idleTimeoutSeconds)) {
            session.revoke();
            refreshSessionRepository.save(session);

            log.info("Idle session revoked for user: {}", userId);

            auditTrailPort.record(new AuditEvent(
                    UUID.randomUUID(),
                    AuditEventType.SECURITY_INCIDENT,
                    userId,
                    userId,
                    LocalDateTime.now(clock),
                    session.getIpAddress(),
                    session.getUserAgent(),
                    AuditResult.SUCCESS,
                    "Session revoked due to inactivity (timeout: " + idleTimeoutSeconds / 60 + " minutes)",
                    null
            ));
        }
    }

    public boolean isSessionActive(UUID userId, String jtiDigest) {
        return refreshSessionRepository.findByJtiDigest(jtiDigest)
                .filter(session -> session.getUserId().equals(userId))
                .filter(session -> session.isActive(clock.instant()))
                .isPresent();
    }
}
