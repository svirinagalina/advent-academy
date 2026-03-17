package com.adventacademy.userservice.application.service.auth;

import com.adventacademy.userservice.application.dto.request.LoginRequest;
import com.adventacademy.userservice.application.dto.response.AuthResponse;
import com.adventacademy.userservice.application.port.JwtProvider;
import com.adventacademy.userservice.application.port.MfaProvider;
import com.adventacademy.userservice.application.port.PasswordEncoder;
import com.adventacademy.userservice.application.service.security.AccountLockService;
import com.adventacademy.userservice.domain.exception.AccountLockedException;
import com.adventacademy.userservice.domain.exception.InvalidCredentialsException;
import com.adventacademy.userservice.domain.exception.InvalidUserStateException;
import com.adventacademy.userservice.domain.exception.UserNotFoundException;
import com.adventacademy.userservice.domain.model.User;
import com.adventacademy.userservice.domain.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Use case service for user authentication.
 * <p>
 * Validates credentials, checks account state (active, not locked), handles
 * optional MFA code verification, tracks failed attempts via {@link AccountLockService},
 * and issues JWT access and refresh tokens on success.
 * </p>
 */
@Service
public class LoginService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final MfaProvider mfaProvider;
    private final AccountLockService accountLockService;
    private final long accessTokenExpirationMinutes;

    public LoginService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtProvider jwtProvider,
            MfaProvider mfaProvider,
            AccountLockService accountLockService,
            @Value("${security.jwt.access-token-expiration-minutes:15}") long accessTokenExpirationMinutes) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
        this.mfaProvider = mfaProvider;
        this.accountLockService = accountLockService;
        this.accessTokenExpirationMinutes = accessTokenExpirationMinutes;
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
            throw new InvalidCredentialsException();
        }

        if (user.isMfaEnabled()) {
            if (request.mfaCode() == null || request.mfaCode().isBlank()) {
                return new AuthResponse(null, null, 0, true);
            }
            if (!mfaProvider.validateCode(user.getMfaSecret(), request.mfaCode())) {
                accountLockService.handleFailedLogin(user);
                throw new InvalidCredentialsException("Invalid MFA code");
            }
        }

        user.resetFailedAttempts();
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        String accessToken = jwtProvider.generateAccessToken(user.getId(), user.getEmail());
        String refreshToken = jwtProvider.generateRefreshToken(user.getId());

        return new AuthResponse(
                accessToken,
                refreshToken,
                accessTokenExpirationMinutes * 60,
                false
        );
    }
}
