package com.adventacademy.userservice.application.service.auth;

import com.adventacademy.userservice.application.dto.request.LoginRequest;
import com.adventacademy.userservice.application.dto.response.AuthResponse;
import com.adventacademy.userservice.application.port.AuditTrailPort;
import com.adventacademy.userservice.application.port.JwtProvider;
import com.adventacademy.userservice.application.port.PasswordEncoder;
import com.adventacademy.userservice.application.service.security.AccountLockService;
import com.adventacademy.userservice.domain.exception.AccountLockedException;
import com.adventacademy.userservice.domain.exception.EmailNotVerifiedException;
import com.adventacademy.userservice.domain.exception.InvalidCredentialsException;
import com.adventacademy.userservice.domain.exception.InvalidUserStateException;
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
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Use case service for user authentication.
 * <p>
 * Validates credentials, checks account state (active, not locked), verifies the second factor
 * (TOTP or a one-time recovery code) when MFA is on, tracks failed attempts via
 * {@link AccountLockService}, and issues JWT access and refresh tokens on success. Every outcome is
 * recorded through {@link AuditTrailPort}.
 * <p>
 * An unknown e-mail and a wrong password produce the same {@link InvalidCredentialsException}: the
 * response must not tell an attacker whether the account exists. Account state is only revealed
 * after the password verified.
 */
@Service
public class LoginService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final MfaCodeVerifier mfaCodeVerifier;
    private final AccountLockService accountLockService;
    private final AuditTrailPort auditTrailPort;
    private final long accessTokenExpirationMinutes;
    private final Clock clock;
    private final String unknownUserPasswordHash;

    public LoginService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtProvider jwtProvider,
            MfaCodeVerifier mfaCodeVerifier,
            AccountLockService accountLockService,
            AuditTrailPort auditTrailPort,
            @Value("${security.jwt.access-token-expiration-minutes:15}") long accessTokenExpirationMinutes,
            Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
        this.mfaCodeVerifier = mfaCodeVerifier;
        this.accountLockService = accountLockService;
        this.auditTrailPort = auditTrailPort;
        this.accessTokenExpirationMinutes = accessTokenExpirationMinutes;
        this.clock = clock;
        this.unknownUserPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    // A refused attempt still commits: the failed-attempt counter behind account locking and the
    // burnt recovery code must not be rolled back together with the 401.
    @Transactional(noRollbackFor = InvalidCredentialsException.class)
    public AuthResponse login(LoginRequest request) {
        Optional<User> maybeUser = userRepository.findByEmailForUpdate(request.email());
        if (maybeUser.isEmpty()) {
            // Unknown accounts must pay the same password-hashing cost as wrong passwords.
            passwordEncoder.matches(request.password(), unknownUserPasswordHash);
            auditTrailPort.record(event(AuditEventType.LOGIN_FAILED, null, request, AuditResult.FAILED, "Unknown email"));
            throw new InvalidCredentialsException();
        }
        User user = maybeUser.get();

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            if (!user.isLocked()) {
                accountLockService.handleFailedLogin(user);
            }
            auditTrailPort.record(event(AuditEventType.LOGIN_FAILED, user.getId(), request, AuditResult.FAILED, "Invalid credentials"));
            throw new InvalidCredentialsException();
        }

        if (!user.isEmailVerified()) {
            throw new EmailNotVerifiedException();
        }
        if (!user.canAuthenticate()) {
            if (user.isLocked()) {
                throw new AccountLockedException(user.getLockedUntil());
            }
            throw new InvalidUserStateException("User account is not active. Status: " + user.getStatus());
        }

        if (user.isMfaEnabled()) {
            if (request.mfaCode() == null || request.mfaCode().isBlank()) {
                return new AuthResponse(null, null, 0, true);
            }
            MfaCodeVerifier.Outcome outcome = mfaCodeVerifier.verify(user, request.mfaCode());
            if (outcome == MfaCodeVerifier.Outcome.REJECTED) {
                accountLockService.handleFailedLogin(user);
                auditTrailPort.record(event(AuditEventType.LOGIN_FAILED, user.getId(), request, AuditResult.FAILED, "Invalid MFA code"));
                throw new InvalidCredentialsException("Invalid MFA code");
            }
            if (outcome == MfaCodeVerifier.Outcome.RECOVERY_CODE) {
                auditTrailPort.record(event(AuditEventType.MFA_RECOVERY_CODE_USED, user.getId(), request, AuditResult.SUCCESS, null));
            }
        }

        user.resetFailedAttempts();
        user.setLastLoginAt(LocalDateTime.now(clock));
        userRepository.save(user);

        String accessToken = jwtProvider.generateAccessToken(
                user.getId(),
                user.getEmail(),
                user.getRoles().stream().map(role -> role.getName()).collect(Collectors.toSet()));
        String refreshToken = jwtProvider.generateRefreshToken(user.getId());

        auditTrailPort.record(event(AuditEventType.LOGIN_SUCCESS, user.getId(), request, AuditResult.SUCCESS, null));

        return new AuthResponse(
                accessToken,
                refreshToken,
                accessTokenExpirationMinutes * 60,
                false
        );
    }

    private AuditEvent event(AuditEventType type, UUID userId, LoginRequest request, AuditResult result, String reason) {
        return new AuditEvent(UUID.randomUUID(), type, userId, userId, LocalDateTime.now(clock),
                request.ipAddress(), request.userAgent(), result, reason, null);
    }
}
