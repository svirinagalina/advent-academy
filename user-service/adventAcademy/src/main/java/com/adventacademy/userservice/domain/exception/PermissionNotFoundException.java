package com.adventacademy.userservice.domain.exception;

import java.util.UUID;

public class PermissionNotFoundException extends RuntimeException {
    public PermissionNotFoundException(UUID permissionId) {
        super("Permission not found with id: " + permissionId);
    }

    public PermissionNotFoundException(String permissionName) {
        super("Permission not found with name: " + permissionName);
    }

    public PermissionNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
