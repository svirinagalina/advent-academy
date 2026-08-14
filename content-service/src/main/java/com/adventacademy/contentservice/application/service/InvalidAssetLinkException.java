package com.adventacademy.contentservice.application.service;

import java.util.Set;

/**
 * Thrown when lesson Markdown references assets that do not exist, belong
 * to a different lesson, or are not valid asset identifiers.
 * <p>
 * Identifiers are kept as raw strings: a malformed reference such as
 * {@code asset:12345} has no UUID to report, but the author still needs to
 * see which text in the Markdown was rejected.
 */
public class InvalidAssetLinkException extends RuntimeException {

    private final Set<String> invalidAssetIds;

    public InvalidAssetLinkException(Set<String> invalidAssetIds) {
        super("Markdown references invalid or foreign assets: " + invalidAssetIds);
        this.invalidAssetIds = Set.copyOf(invalidAssetIds);
    }

    public Set<String> getInvalidAssetIds() {
        return invalidAssetIds;
    }
}
