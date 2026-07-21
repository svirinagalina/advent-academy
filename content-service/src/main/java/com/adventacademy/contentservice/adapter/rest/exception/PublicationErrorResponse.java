package com.adventacademy.contentservice.adapter.rest.exception;

import java.util.Set;
import java.util.UUID;

/**
 * 422 body for publication attempts: which referenced entities are not ready.
 */
public record PublicationErrorResponse(String message, Set<UUID> notReadyIds) {
}
