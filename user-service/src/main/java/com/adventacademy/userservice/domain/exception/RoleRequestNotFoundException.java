package com.adventacademy.userservice.domain.exception;

import java.util.UUID;

public class RoleRequestNotFoundException extends RuntimeException {
    public RoleRequestNotFoundException(UUID roleRequestId) {
        super("Role request not found with id: " + roleRequestId);
    }
}
