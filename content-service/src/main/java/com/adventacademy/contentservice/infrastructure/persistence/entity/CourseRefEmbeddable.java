package com.adventacademy.contentservice.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.util.UUID;

@Embeddable
public class CourseRefEmbeddable {

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(nullable = false)
    private int position;

    public CourseRefEmbeddable() {
    }

    public CourseRefEmbeddable(UUID courseId, int position) {
        this.courseId = courseId;
        this.position = position;
    }

    public UUID getCourseId() {
        return courseId;
    }

    public int getPosition() {
        return position;
    }

    public void setCourseId(UUID courseId) {
        this.courseId = courseId;
    }

    public void setPosition(int position) {
        this.position = position;
    }
}