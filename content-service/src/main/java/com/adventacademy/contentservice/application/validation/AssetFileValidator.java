package com.adventacademy.contentservice.application.validation;

import com.adventacademy.contentservice.application.command.UploadAssetCommand;

import java.util.Set;

public class AssetFileValidator {

    private final long maxFileSizeBytes;
    private final Set<String> allowedMimeTypes;

    public AssetFileValidator(long maxFileSizeBytes, Set<String> allowedMimeTypes) {
        this.maxFileSizeBytes = maxFileSizeBytes;
        this.allowedMimeTypes = allowedMimeTypes;
    }

    public void validate(UploadAssetCommand command) {
        if (command == null || command.content() == null || command.content().length == 0) {
            throw new InvalidAssetFileException("file must not be empty");
        }

        String filename = command.filename();
        if (filename == null || filename.isBlank()) {
            throw new InvalidAssetFileException("filename must not be blank");
        }

        if (filename.length() > 255 || filename.chars().anyMatch(Character::isISOControl)) {
            throw new InvalidAssetFileException("filename must be at most 255 characters without control characters");
        }
        String contentType = command.contentType();
        if (contentType == null || contentType.isBlank()) {
            throw new InvalidAssetFileException("content type must not be blank");
        }

        if (!allowedMimeTypes.contains(contentType)) {
            throw new InvalidAssetFileException(
                    "unsupported content type: " + contentType +
                    ". Allowed types: " + allowedMimeTypes);
        }

        if (command.sizeBytes() > maxFileSizeBytes) {
            throw new InvalidAssetFileException(
                    "file size " + command.sizeBytes() + " exceeds limit " + maxFileSizeBytes);
        }
        if (!AssetTypePolicy.matches(filename, contentType, command.content())) {
            throw new InvalidAssetFileException("file signature, extension and content type must match; active formats are forbidden");
        }
    }
}
