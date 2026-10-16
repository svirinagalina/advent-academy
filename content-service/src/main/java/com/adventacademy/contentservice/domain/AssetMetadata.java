package com.adventacademy.contentservice.domain;

import java.time.Instant;
import java.util.UUID;

/** Authorization/link checks must not load the binary body. */
public record AssetMetadata(UUID id, UUID lessonId, UUID ownerId, String filename, String contentType,
                            long fileSize, Instant createdAt, Instant updatedAt) {
    public static AssetMetadata from(StoredAsset asset) {
        return new AssetMetadata(asset.getId(),asset.getLessonId(),asset.getOwnerId(),asset.getFilename(),
                asset.getContentType(),asset.getFileSize(),asset.getCreatedAt(),asset.getUpdatedAt());
    }
}
