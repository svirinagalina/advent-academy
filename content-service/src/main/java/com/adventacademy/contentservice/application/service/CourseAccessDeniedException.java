package com.adventacademy.contentservice.application.service;

import java.util.UUID;

/**
 * Thrown when the current user is not allowed to see or modify the given course.
 * <p>
 * A course may be curated by its owner, by an {@code ADMIN} and by a {@code CONTENT_EDITOR};
 * every other authenticated user is limited to their own courses.
 */
public class CourseAccessDeniedException extends RuntimeException {

    private final UUID courseId;

    public CourseAccessDeniedException(UUID courseId) {
        super("Course " + courseId + " belongs to another user");
        this.courseId = courseId;
    }

    public UUID getCourseId() {
        return courseId;
    }
}
