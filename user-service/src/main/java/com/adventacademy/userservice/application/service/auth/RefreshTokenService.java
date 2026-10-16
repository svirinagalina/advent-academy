package com.adventacademy.userservice.application.service.auth;

import com.adventacademy.userservice.application.dto.request.RefreshTokenRequest;
import com.adventacademy.userservice.application.dto.response.AuthResponse;
import com.adventacademy.userservice.application.port.AuditTrailPort;
import com.adventacademy.userservice.application.port.JwtProvider;
import com.adventacademy.userservice.domain.exception.InvalidUserStateException;
import com.adventacademy.userservice.domain.exception.InvalidCredentialsException;
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
public class RefreshTokenService {

    private final UserRepository userRepository;
    private final JwtProvider jwtProvider;
    private final AuditTrailPort auditTrailPort;
    private final Clock clock;
    private final long accessTokenExpirationMinutes;

    public RefreshTokenService(
            UserRepository userRepository,
            JwtProvider jwtProvider,
            AuditTrailPort auditTrailPort,
            Clock clock,
            @Value("${security.jwt.access-token-expiration-minutes:15}") long accessTokenExpirationMinutes) {
        this.userRepository = userRepository;
        this.jwtProvider = jwtProvider;
        this.auditTrailPort = auditTrailPort;
        this.clock = clock;
        this.accessTokenExpirationMinutes = accessTokenExpirationMinutes;
    }

    @Transactional(noRollbackFor = InvalidCredentialsException.class)
    public AuthResponse refresh(RefreshTokenRequest request) {
        if (!jwtProvider.validateRefreshToken(request.refreshToken())) {
            jwtProvider.revokeReplayedRefreshToken(request.refreshToken());
            throw new InvalidCredentialsException("Invalid refresh token");
        }

        UUID userId = jwtProvider.getUserIdFromToken(request.refreshToken());
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (!user.canAuthenticate()) {
            throw new InvalidUserStateException("User account is not active. Status: " + user.getStatus());
        }

        // Serialize with login and password reset/change before consuming and rotating tokens.
        if (!jwtProvider.consumeRefreshToken(request.refreshToken())) {
            jwtProvider.revokeReplayedRefreshToken(request.refreshToken());
            throw new InvalidCredentialsException("Invalid refresh token");
        }

        String accessToken = jwtProvider.generateAccessToken(
                user.getId(),
                user.getEmail(),
                user.getRoles().stream().map(role -> role.getName()).collect(java.util.stream.Collectors.toSet()));
        String refreshToken = jwtProvider.rotateRefreshToken(request.refreshToken());

        auditTrailPort.record(new AuditEvent(
                UUID.randomUUID(),
                AuditEventType.REFRESH_TOKEN_UPDATED,
                user.getId(),
                user.getId(),
                LocalDateTime.now(clock),
                request.ipAddress(),
                request.userAgent(),
                AuditResult.SUCCESS,
                null,
                null
        ));

        return new AuthResponse(
                accessToken,
                refreshToken,
                accessTokenExpirationMinutes * 60,
                false
        );
    }
}
