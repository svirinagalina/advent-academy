package com.adventacademy.userservice.domain.exception;

import java.util.UUID;

public class DuplicateActiveRoleRequestException extends RuntimeException {
    public DuplicateActiveRoleRequestException(UUID userId, UUID requestedRoleId) {
        super("Active role request already exists for user " + userId + " and role " + requestedRoleId);
    }
}
