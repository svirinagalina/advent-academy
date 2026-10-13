package com.adventacademy.contentservice.adapter.rest.dto;

import java.time.Instant;
import java.util.UUID;

public record AssetResponse(
        UUID id,
        UUID lessonId,
        String filename,
        String contentType,
        long fileSize,
        String downloadUrl,
        Instant createdAt,
        Instant updatedAt
) {
}
