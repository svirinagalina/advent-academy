package com.adventacademy.contentservice.adapter.rest.dto;

import com.adventacademy.contentservice.domain.constructor.Component;
import com.adventacademy.contentservice.domain.constructor.PublicationStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ComponentResponse(
        UUID id,
        @Schema(description = "Plain text. Render with a text-only sink (e.g. textContent), never innerHTML.")
        String title,
        @Schema(description = "Plain text. Render with a text-only sink (e.g. textContent), never innerHTML.")
        String description,
        PublicationStatus status,
        List<LessonResponse> lessons,
        Instant createdAt,
        Instant updatedAt
) {
    public record LessonResponse(
            UUID id,
            @Schema(description = "Plain text. Render with a text-only sink (e.g. textContent), never innerHTML.")
            String title,
            int position) {
    }

    public static ComponentResponse from(Component component) {
        List<LessonResponse> lessons = component.getLessons().stream()
                .map(l -> new LessonResponse(l.getId(), l.getTitle(), l.getPosition()))
                .toList();
        return new ComponentResponse(
                component.getId(),
                component.getTitle(),
                component.getDescription(),
                component.getStatus(),
                lessons,
                component.getCreatedAt(),
                component.getUpdatedAt());
    }
}