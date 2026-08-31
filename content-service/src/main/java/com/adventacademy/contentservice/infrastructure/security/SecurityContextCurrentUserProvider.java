package com.adventacademy.contentservice.infrastructure.security;

import com.adventacademy.contentservice.application.port.CurrentUserProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Reads the current user id from the Spring Security context.
 * <p>
 * {@link com.adventacademy.contentservice.infrastructure.security.JwtAuthenticationFilter} stores
 * the JWT subject (the user id) as the authentication principal, so the principal is parsed back
 * into a {@link UUID} here. Anonymous requests yield {@link Optional#empty()}.
 */
@Component
public class SecurityContextCurrentUserProvider implements CurrentUserProvider {

    @Override
    public Optional<UUID> currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof UUID userId) {
            return Optional.of(userId);
        }
        if (principal instanceof String subject) {
            try {
                return Optional.of(UUID.fromString(subject));
            } catch (IllegalArgumentException ignored) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }
}
