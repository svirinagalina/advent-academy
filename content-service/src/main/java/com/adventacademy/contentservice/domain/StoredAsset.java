package com.adventacademy.contentservice.domain;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class StoredAsset {

    private final UUID id;
    private final UUID lessonId;
    private final String filename;
    private final String contentType;
    private final long fileSize;
    private final byte[] fileData;
    private final Instant createdAt;
    private Instant updatedAt;

    private StoredAsset(
            UUID id,
            UUID lessonId,
            String filename,
            String contentType,
            long fileSize,
            byte[] fileData,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.lessonId = lessonId;
        this.filename = filename;
        this.contentType = contentType;
        this.fileSize = fileSize;
        this.fileData = fileData;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static StoredAsset create(
            UUID lessonId,
            String filename,
            String contentType,
            byte[] fileData
    ) {
        return create(lessonId, filename, contentType, fileData, Clock.systemUTC());
    }

    public static StoredAsset create(
            UUID lessonId,
            String filename,
            String contentType,
            byte[] fileData,
            Clock clock
    ) {
        Objects.requireNonNull(clock, "clock must not be null");
        validateCreation(lessonId, filename, contentType, fileData);

        Instant now = clock.instant();
        return new StoredAsset(
                UUID.randomUUID(),
                lessonId,
                filename,
                contentType,
                fileData.length,
                fileData,
                now,
                now
        );
    }

    public static StoredAsset restore(
            UUID id,
            UUID lessonId,
            String filename,
            String contentType,
            long fileSize,
            byte[] fileData,
            Instant createdAt,
            Instant updatedAt
    ) {
        return new StoredAsset(
                id,
                lessonId,
                filename,
                contentType,
                fileSize,
                fileData,
                createdAt,
                updatedAt
        );
    }

    private static void validateCreation(
            UUID lessonId,
            String filename,
            String contentType,
            byte[] fileData
    ) {
        if (lessonId == null) {
            throw new IllegalArgumentException("lessonId must not be null");
        }
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("filename must not be blank");
        }
        if (contentType == null || contentType.isBlank()) {
            throw new IllegalArgumentException("contentType must not be blank");
        }
        if (fileData == null || fileData.length == 0) {
            throw new IllegalArgumentException("fileData must not be empty");
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getLessonId() {
        return lessonId;
    }

    public String getFilename() {
        return filename;
    }

    public String getContentType() {
        return contentType;
    }

    public long getFileSize() {
        return fileSize;
    }

    public byte[] getFileData() {
        return fileData;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
