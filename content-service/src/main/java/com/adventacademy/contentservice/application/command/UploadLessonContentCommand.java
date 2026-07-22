package com.adventacademy.contentservice.application.command;

import java.util.UUID;

/**
 * Framework-agnostic input of the "upload lesson content" use-case.
 * <p>
 * The REST adapter reads the HTTP multipart file once and turns it into this value
 * object, so the application layer works with plain data ({@code lessonId}, file
 * metadata and the raw bytes) instead of Spring Web types.
 *
 * @param lessonId    lesson the content belongs to
 * @param filename    original file name, used for extension validation
 * @param contentType declared MIME type, used for content-type validation
 * @param content     raw file bytes, read exactly once by the adapter
 */
public record UploadLessonContentCommand(
        UUID lessonId,
        String filename,
        String contentType,
        byte[] content) {

    /** @return the size of the uploaded content in bytes. */
    public long sizeBytes() {
        return content == null ? 0 : content.length;
    }
}
