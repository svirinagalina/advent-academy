package com.adventacademy.userservice.application.service.auth;

import com.adventacademy.userservice.application.dto.response.AuthResponse;
import com.adventacademy.userservice.application.port.AuditTrailPort;
import com.adventacademy.userservice.application.port.JwtProvider;
import com.adventacademy.userservice.domain.exception.InvalidCredentialsException;
import com.adventacademy.userservice.domain.model.AuditEvent;
import com.adventacademy.userservice.domain.model.AuditEventType;
import com.adventacademy.userservice.domain.model.AuditResult;
import com.adventacademy.userservice.domain.model.RefreshSession;
import com.adventacademy.userservice.domain.model.User;
import com.adventacademy.userservice.domain.repository.RefreshSessionRepository;
import com.adventacademy.userservice.domain.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class RefreshTokenServiceV2 {

    private final UserRepository userRepository;
    private final RefreshSessionRepository refreshSessionRepository;
    private final JwtProvider jwtProvider;
    private final AuditTrailPort auditTrailPort;
    private final long accessTokenExpirationMinutes;
    private final long refreshTokenExpirationDays;
    private final Clock clock;

    public RefreshTokenServiceV2(
            UserRepository userRepository,
            RefreshSessionRepository refreshSessionRepository,
            JwtProvider jwtProvider,
            AuditTrailPort auditTrailPort,
            @Value("${security.jwt.access-token-expiration-minutes:15}") long accessTokenExpirationMinutes,
            @Value("${security.jwt.refresh-token-expiration-days:7}") long refreshTokenExpirationDays,
            Clock clock) {
        this.userRepository = userRepository;
        this.refreshSessionRepository = refreshSessionRepository;
        this.jwtProvider = jwtProvider;
        this.auditTrailPort = auditTrailPort;
        this.accessTokenExpirationMinutes = accessTokenExpirationMinutes;
        this.refreshTokenExpirationDays = refreshTokenExpirationDays;
        this.clock = clock;
    }

    @Transactional
    public AuthResponse refreshToken(String jti, String deviceId, String userAgent, String ipAddress) {
        String jtiDigest = hashJti(jti);

        // Find session by JTI digest
        RefreshSession session = refreshSessionRepository.findByJtiDigest(jtiDigest)
                .orElseThrow(() -> {
                    log.warn("Refresh token not found or invalid");
                    return new InvalidCredentialsException("Invalid refresh token");
                });

        User user = userRepository.findById(session.getUserId())
                .orElseThrow(() -> new InvalidCredentialsException("User not found"));

        Instant now = clock.instant();

        // Check if session is active
        if (!session.isActive(now)) {
            log.warn("Refresh session is not active for user: {}", user.getId());
            throw new InvalidCredentialsException("Refresh session is not active");
        }

        // Check if token was already used (reuse detection)
        if (!session.getStatus().equals(RefreshSession.Status.ACTIVE)) {
            log.error("Refresh token reuse detected for user: {}. Revoking entire family.", user.getId());

            // Revoke entire token family
            List<RefreshSession> familySessions = refreshSessionRepository.findByTokenFamilyId(session.getTokenFamilyId());
            for (RefreshSession familySession : familySessions) {
                familySession.revoke();
                refreshSessionRepository.save(familySession);
            }

            auditTrailPort.record(new AuditEvent(
                    UUID.randomUUID(),
                    AuditEventType.SECURITY_INCIDENT,
                    user.getId(),
                    user.getId(),
                    LocalDateTime.now(clock),
                    ipAddress,
                    userAgent,
                    AuditResult.FAILED,
                    "Refresh token reuse detected. Token family revoked.",
                    null
            ));

            throw new InvalidCredentialsException("Refresh token has been revoked due to reuse detection");
        }

        // Mark current session as used and create new one
        session.markAsUsed();

        // Generate new tokens
        String newAccessToken = jwtProvider.generateAccessToken(
                user.getId(),
                user.getEmail(),
                user.getRoles().stream()
                        .map(role -> role.getName())
                        .collect(java.util.stream.Collectors.toSet())
        );

        String newRefreshToken = jwtProvider.generateRefreshToken(user.getId());
        String newJti = extractJtiFromToken(newRefreshToken);
        String newJtiDigest = hashJti(newJti);

        // Create new session in same family
        Instant expiresAt = now.plusSeconds(refreshTokenExpirationDays * 86400);
        RefreshSession newSession = RefreshSession.create(
                user.getId(),
                session.getTokenFamilyId(),  // Same family
                newJtiDigest,
                deviceId,
                userAgent,
                ipAddress,
                userAgent,
                expiresAt
        );

        // Save sessions atomically
        session.rotate(newJtiDigest, expiresAt);
        refreshSessionRepository.save(session);
        refreshSessionRepository.save(newSession);

        log.info("Token refreshed successfully for user: {}", user.getId());

        auditTrailPort.record(new AuditEvent(
                UUID.randomUUID(),
                AuditEventType.TOKEN_REFRESHED,
                user.getId(),
                user.getId(),
                LocalDateTime.now(clock),
                ipAddress,
                userAgent,
                AuditResult.SUCCESS,
                null,
                null
        ));

        return new AuthResponse(
                newAccessToken,
                newRefreshToken,
                accessTokenExpirationMinutes * 60,
                false
        );
    }

    private String hashJti(String jti) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(jti.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    private String extractJtiFromToken(String token) {
        // This is a simplified approach - in production, you'd parse the JWT claims properly
        // For now, we'll extract from JwtProvider which already knows the structure
        // This will be properly handled when we have the full JWT structure
        return UUID.randomUUID().toString();
    }
}
