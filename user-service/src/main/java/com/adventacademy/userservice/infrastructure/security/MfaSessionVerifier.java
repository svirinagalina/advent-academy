package com.adventacademy.userservice.infrastructure.security;

import com.adventacademy.userservice.application.port.PasswordEncoder;
import com.adventacademy.userservice.domain.model.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Step-up verification for MFA operations.
 * Users must provide their current password to enable/disable MFA.
 * Protects against unauthorized MFA changes.
 */
@Slf4j
@Component
public class MfaSessionVerifier {

    private final PasswordEncoder passwordEncoder;

    public MfaSessionVerifier(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Verify user password for MFA operations.
     *
     * @param user The authenticated user
     * @param plainPassword Password to verify (from request)
     * @throws IllegalArgumentException if password is invalid
     */
    public void verifyPassword(User user, String plainPassword) {
        if (plainPassword == null || plainPassword.isEmpty()) {
            throw new IllegalArgumentException("Password required for MFA operations");
        }

        if (!passwordEncoder.matches(plainPassword, user.getPasswordHash())) {
            log.warn("MFA session verification failed for user {}", user.getId());
            throw new IllegalArgumentException("Invalid password");
        }

        log.debug("MFA session verified for user {}", user.getId());
    }

    /**
     * Check if MFA is currently enabled for user.
     */
    public boolean isMfaEnabled(User user) {
        return user.isMfaEnabled();
    }

    /**
     * Check if MFA is disabled for user.
     */
    public boolean isMfaDisabled(User user) {
        return !user.isMfaEnabled();
    }
}
