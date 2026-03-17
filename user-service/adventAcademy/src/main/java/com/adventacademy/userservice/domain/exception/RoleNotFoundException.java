package com.adventacademy.userservice.domain.exception;

import java.util.UUID;

public class RoleNotFoundException extends RuntimeException {
    public RoleNotFoundException(UUID roleId) {
        super("Role not found with id: " + roleId);
    }

    public RoleNotFoundException(String roleName) {
        super("Role not found with name: " + roleName);
    }

    public RoleNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
