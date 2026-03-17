package com.adventacademy.userservice.domain.exception;

public class InvalidUserStateException extends RuntimeException {
    public InvalidUserStateException(String message) {
        super(message);
    }

    public InvalidUserStateException(String message, Throwable cause) {
        super(message, cause);
    }
}
