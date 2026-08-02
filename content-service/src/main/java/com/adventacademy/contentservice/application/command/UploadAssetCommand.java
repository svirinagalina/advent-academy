package com.adventacademy.contentservice.application.command;

import java.util.UUID;

public record UploadAssetCommand(
        UUID lessonId,
        String filename,
        String contentType,
        byte[] content
) {
    public long sizeBytes() {
        return content != null ? content.length : 0;
    }
}
