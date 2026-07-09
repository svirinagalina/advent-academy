package com.adventacademy.contentservice.application.service;

public class LessonContentAlreadyExistsException extends RuntimeException {
    public LessonContentAlreadyExistsException(String message) {
        super(message);
    }
}
