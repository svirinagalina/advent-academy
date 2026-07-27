package com.adventacademy.userservice.domain.exception;

public class InvalidRoleRequestStateException extends RuntimeException {
    public InvalidRoleRequestStateException(String message) {
        super(message);
    }
}
