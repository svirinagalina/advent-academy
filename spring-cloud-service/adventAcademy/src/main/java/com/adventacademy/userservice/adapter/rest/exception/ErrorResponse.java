package com.adventacademy.userservice.adapter.rest.exception;

import java.time.LocalDateTime;

public record ErrorResponse(
        String errorCode,
        String message,
        LocalDateTime timestamp
) {
    public ErrorResponse(String errorCode, String message) {
        this(errorCode, message, LocalDateTime.now());
    }
}
