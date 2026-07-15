package com.adventacademy.contentservice.domain.constructor;

import java.util.Objects;
import java.util.UUID;

/**
 * Ordered reference from a {@link Direction} to a {@link Course}.
 * <p>
 * An immutable value object carrying the referenced {@code courseId} and its
 * {@code position} within the owning direction, so ordering is part of the domain model.
 * Two references are equal when they point to the same course at the same position.
 */
public final class CourseRef {

    private final UUID courseId;
    private final int position;

    private CourseRef(UUID courseId, int position) {
        this.courseId = courseId;
        this.position = position;
    }

    public static CourseRef create(UUID courseId, int position) {
        if (courseId == null) {
            throw new IllegalArgumentException("courseId must not be null");
        }
        if (position < 0) {
            throw new IllegalArgumentException("position must not be negative");
        }
        return new CourseRef(courseId, position);
    }

    public static CourseRef restore(UUID courseId, int position) {
        return new CourseRef(courseId, position);
    }

    public UUID getCourseId() {
        return courseId;
    }

    public int getPosition() {
        return position;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CourseRef other)) {
            return false;
        }
        return position == other.position && Objects.equals(courseId, other.courseId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(courseId, position);
    }
}
