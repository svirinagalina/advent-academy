package com.adventacademy.contentservice.adapter.rest.dto;

import com.adventacademy.contentservice.domain.ContentStatus;

import java.time.Instant;
import java.util.UUID;

public record LessonContentResponse(
        UUID id,
        UUID lessonId,
        String markdown,
        String html,
        ContentStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}