package com.adventacademy.contentservice.application.service;

import java.util.Set;
import java.util.UUID;

/**
 * Thrown when lesson Markdown references assets that do not exist or belong
 * to a different lesson.
 */
public class InvalidAssetLinkException extends RuntimeException {

    private final Set<UUID> invalidAssetIds;

    public InvalidAssetLinkException(Set<UUID> invalidAssetIds) {
        super("Markdown references invalid or foreign assets: " + invalidAssetIds);
        this.invalidAssetIds = Set.copyOf(invalidAssetIds);
    }

    public Set<UUID> getInvalidAssetIds() {
        return invalidAssetIds;
    }
}