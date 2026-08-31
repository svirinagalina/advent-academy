package com.adventacademy.contentservice.application.port;

import java.util.Optional;
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
     * @return the id of the authenticated user
     * @throws IllegalStateException when there is no authenticated user in the current context
     */
    default UUID requireCurrentUserId() {
        return currentUserId().orElseThrow(
                () -> new IllegalStateException("No authenticated user in the current security context"));
    }
}
