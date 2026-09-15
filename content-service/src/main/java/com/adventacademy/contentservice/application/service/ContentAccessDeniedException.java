package com.adventacademy.contentservice.application.service;

import java.util.UUID;

/**
 * Thrown when the current user is not allowed to see or modify the given piece of content.
 * <p>
 * Content may be curated by its owner, by an {@code ADMIN} and by a {@code CONTENT_EDITOR};
 * every other authenticated user is limited to the content they created themselves.
 */
public class ContentAccessDeniedException extends RuntimeException {

    private final String resourceType;
    private final UUID resourceId;

    public ContentAccessDeniedException(String resourceType, UUID resourceId) {
        super(resourceType + " " + resourceId + " belongs to another user");
        this.resourceType = resourceType;
        this.resourceId = resourceId;
    }

    public String getResourceType() {
        return resourceType;
    }

    public UUID getResourceId() {
        return resourceId;
    }
}
