package com.adventacademy.contentservice.application.port;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Port giving the application layer access to the user the current request is executed for.
 * <p>
 * The implementation lives in the infrastructure layer and reads the authenticated principal
 * established by the JWT filter, so use-cases stay free of Spring Security types.
 */
public interface CurrentUserProvider {

    /**
     * @return the id of the authenticated user, or {@link Optional#empty()} when the request is anonymous
     */
    Optional<UUID> currentUserId();

    /**
     * @return the roles granted to the authenticated user, without the {@code ROLE_} prefix and in
     *         upper case; an empty set for anonymous requests
     */
    default Set<String> currentRoles() {
        return Set.of();
    }

    /**
     * @return the id of the authenticated user
     * @throws IllegalStateException when there is no authenticated user in the current context
     */
    default UUID requireCurrentUserId() {
        return currentUserId().orElseThrow(
                () -> new IllegalStateException("No authenticated user in the current security context"));
    }

    /**
     * @return {@code true} when the authenticated user holds the given role (case-insensitive,
     *         without the {@code ROLE_} prefix)
     */
    default boolean hasRole(String role) {
        return role != null && currentRoles().contains(role.toUpperCase(Locale.ROOT));
    }

    /**
     * @return {@code true} when the authenticated user holds at least one of the given roles
     */
    default boolean hasAnyRole(String... roles) {
        if (roles == null) {
            return false;
        }
        for (String role : roles) {
            if (hasRole(role)) {
                return true;
            }
        }
        return false;
    }
}
