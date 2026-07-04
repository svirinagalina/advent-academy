package com.adventacademy.contentservice.application.validation;


public class InvalidMarkdownFileException extends RuntimeException {


    public InvalidMarkdownFileException(String message) {
        super(message);
    }

    public InvalidMarkdownFileException(String message, Throwable cause) {
        super(message, cause);
    }
}
