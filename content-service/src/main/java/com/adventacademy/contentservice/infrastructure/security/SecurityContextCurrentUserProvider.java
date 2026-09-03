package com.adventacademy.contentservice.infrastructure.security;

import com.adventacademy.contentservice.application.port.CurrentUserProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Reads the current user id from the Spring Security context.
 * <p>
 * {@link com.adventacademy.contentservice.infrastructure.security.JwtAuthenticationFilter} stores
 * the JWT subject (the user id) as the authentication principal, so the principal is parsed back
 * into a {@link UUID} here. Anonymous requests yield {@link Optional#empty()}.
 */
@Component
public class SecurityContextCurrentUserProvider implements CurrentUserProvider {

    private static final String ROLE_PREFIX = "ROLE_";

    @Override
    public Optional<UUID> currentUserId() {
        Authentication authentication = currentAuthentication();
        if (authentication == null) {
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

    /**
     * Exposes the granted authorities as bare role names, so the application layer can express
     * resource-based rules ("the owner, an ADMIN or a CONTENT_EDITOR") without importing
     * Spring Security types.
     */
    @Override
    public Set<String> currentRoles() {
        Authentication authentication = currentAuthentication();
        if (authentication == null) {
            return Set.of();
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority != null && !authority.isBlank())
                .map(authority -> authority.startsWith(ROLE_PREFIX) ? authority.substring(ROLE_PREFIX.length()) : authority)
                .map(role -> role.toUpperCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    private Authentication currentAuthentication() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        return authentication;
    }
}
