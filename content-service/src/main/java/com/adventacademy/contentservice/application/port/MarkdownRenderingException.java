package com.adventacademy.contentservice.application.port;

public class MarkdownRenderingException extends RuntimeException {
    public MarkdownRenderingException(String message) {
        super(message);
    }

    public MarkdownRenderingException(String message, Throwable cause) {
        super(message, cause);
    }
}
