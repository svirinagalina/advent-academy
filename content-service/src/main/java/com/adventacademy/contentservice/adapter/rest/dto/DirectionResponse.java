package com.adventacademy.contentservice.adapter.rest.dto;

import com.adventacademy.contentservice.domain.constructor.Direction;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DirectionResponse(
        UUID id,
        String title,
        String description,
        List<CourseRefResponse> courses,
        Instant createdAt,
        Instant updatedAt
) {
    public record CourseRefResponse(UUID courseId, int position) {
    }

    public static DirectionResponse from(Direction direction) {
        List<CourseRefResponse> courses = direction.getCourses().stream()
                .map(ref -> new CourseRefResponse(ref.getCourseId(), ref.getPosition()))
                .toList();
        return new DirectionResponse(
                direction.getId(),
                direction.getTitle(),
                direction.getDescription(),
                courses,
                direction.getCreatedAt(),
                direction.getUpdatedAt());
    }
}
