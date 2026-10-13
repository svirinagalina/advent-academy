package com.adventacademy.contentservice.application.service;

import java.util.UUID;

public class CourseInUseException extends RuntimeException {

    public CourseInUseException(UUID courseId) {
        super("Course is used by at least one direction and cannot be deleted: " + courseId);
    }
}
