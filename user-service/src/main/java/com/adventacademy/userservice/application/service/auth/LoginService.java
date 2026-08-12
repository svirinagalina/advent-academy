package com.adventacademy.userservice.application.service.auth;

import com.adventacademy.userservice.application.dto.request.LoginRequest;
import com.adventacademy.userservice.application.dto.response.AuthResponse;
import com.adventacademy.userservice.application.port.AuditTrailPort;
import com.adventacademy.userservice.application.port.JwtProvider;
import com.adventacademy.userservice.application.port.MfaProvider;
import com.adventacademy.userservice.application.port.PasswordEncoder;
import com.adventacademy.userservice.application.service.security.AccountLockService;
import com.adventacademy.userservice.domain.exception.AccountLockedException;
import com.adventacademy.userservice.domain.exception.InvalidCredentialsException;
import com.adventacademy.userservice.domain.exception.InvalidUserStateException;
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

/**
 * Use case service for user authentication.
 * <p>
 * Validates credentials, checks account state (active, not locked), handles
 * optional MFA code verification, tracks failed attempts via {@link AccountLockService},
 * and issues JWT access and refresh tokens on success. Every outcome (success,
 * invalid credentials, locked account, MFA challenge) is recorded through
 * {@link AuditTrailPort} for security auditing.
 * </p>
 */
@Service
public class LoginService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final MfaProvider mfaProvider;
    private final AccountLockService accountLockService;
    private final AuditTrailPort auditTrailPort;
    private final long accessTokenExpirationMinutes;
    private final Clock clock;

    public LoginService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtProvider jwtProvider,
            MfaProvider mfaProvider,
            AccountLockService accountLockService,
            AuditTrailPort auditTrailPort,
            @Value("${security.jwt.access-token-expiration-minutes:15}") long accessTokenExpirationMinutes,
            Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
        this.mfaProvider = mfaProvider;
        this.accountLockService = accountLockService;
        this.auditTrailPort = auditTrailPort;
        this.accessTokenExpirationMinutes = accessTokenExpirationMinutes;
        this.clock = clock;
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new UserNotFoundException(request.email()));

        if (!user.canAuthenticate()) {
            if (user.isLocked()) {
                throw new AccountLockedException(user.getLockedUntil());
            }
            throw new InvalidUserStateException("User account is not active. Status: " + user.getStatus());
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            accountLockService.handleFailedLogin(user);
            auditTrailPort.record(new AuditEvent(
                    UUID.randomUUID(),
                    AuditEventType.LOGIN_FAILED,
                    user.getId(),
                    user.getId(),
                    LocalDateTime.now(clock),
                    request.ipAddress(),
                    request.userAgent(),
                    AuditResult.FAILED,
                    "Invalid credentials",
                    null
            ));
            throw new InvalidCredentialsException();
        }

        if (user.isMfaEnabled()) {
            if (request.mfaCode() == null || request.mfaCode().isBlank()) {
                return new AuthResponse(null, null, 0, true);
            }
            if (!mfaProvider.validateCode(user.getMfaSecret(), request.mfaCode())) {
                accountLockService.handleFailedLogin(user);
                auditTrailPort.record(new AuditEvent(
                        UUID.randomUUID(),
                        AuditEventType.LOGIN_FAILED,
                        user.getId(),
                        user.getId(),
                        LocalDateTime.now(clock),
                        request.ipAddress(),
                        request.userAgent(),
                        AuditResult.FAILED,
                        "Invalid MFA code",
                        null
                ));
                throw new InvalidCredentialsException("Invalid MFA code");
            }
        }

        user.resetFailedAttempts();
        user.setLastLoginAt(LocalDateTime.now(clock));
        userRepository.save(user);

        String accessToken = jwtProvider.generateAccessToken(
                user.getId(),
                user.getEmail(),
                user.getRoles().stream().map(role -> role.getName()).collect(java.util.stream.Collectors.toSet()));
        String refreshToken = jwtProvider.generateRefreshToken(user.getId());

        auditTrailPort.record(new AuditEvent(
                UUID.randomUUID(),
                AuditEventType.LOGIN_SUCCESS,
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
