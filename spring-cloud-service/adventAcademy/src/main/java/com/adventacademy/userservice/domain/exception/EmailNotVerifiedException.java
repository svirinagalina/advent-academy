package com.adventacademy.userservice.domain.exception;

public class EmailNotVerifiedException extends RuntimeException {
    public EmailNotVerifiedException() {
        super("Email address has not been verified");
    }

    public EmailNotVerifiedException(String email) {
        super("Email address has not been verified: " + email);
    }

    public EmailNotVerifiedException(String message, Throwable cause) {
        super(message, cause);
    }
}
