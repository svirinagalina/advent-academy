package com.adventacademy.userservice.domain.exception;

import java.util.UUID;

public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(UUID userId) {
        super("User not found with id: " + userId);
    }

    public UserNotFoundException(String email) {
        super("User not found with email: " + email);
    }

    public UserNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
