package com.adventacademy.contentservice.application.validation;

public class InvalidAssetFileException extends RuntimeException {

    public InvalidAssetFileException(String message) {
        super(message);
    }

    public InvalidAssetFileException(String message, Throwable cause) {
        super(message, cause);
    }
}
