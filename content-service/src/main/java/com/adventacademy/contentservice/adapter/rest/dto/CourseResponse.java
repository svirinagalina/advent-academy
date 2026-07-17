package com.adventacademy.contentservice.adapter.rest.dto;

import com.adventacademy.contentservice.domain.constructor.Course;
import com.adventacademy.contentservice.domain.constructor.PublicationStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CourseResponse(
        UUID id,
        String title,
        String description,
        PublicationStatus status,
        List<ComponentRefResponse> components,
        Instant createdAt,
        Instant updatedAt
) {
    public record ComponentRefResponse(UUID componentId, int position) {
    }

    public static CourseResponse from(Course course) {
        List<ComponentRefResponse> components = course.getComponents().stream()
                .map(ref -> new ComponentRefResponse(ref.getComponentId(), ref.getPosition()))
                .toList();
        return new CourseResponse(
                course.getId(),
                course.getTitle(),
                course.getDescription(),
                course.getStatus(),
                components,
                course.getCreatedAt(),
                course.getUpdatedAt());
    }
}
