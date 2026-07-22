package com.adventacademy.contentservice.application.validation;

import com.adventacademy.contentservice.application.command.UploadLessonContentCommand;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Validates that an uploaded lesson file is a non-empty, size-bounded, UTF-8 Markdown
 * document. A plain domain-service style validator: it works on the framework-agnostic
 * {@link UploadLessonContentCommand} and carries no Spring Web dependency.
 */
public class MarkdownFileValidator {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "text/markdown",
            "text/x-markdown",
            "text/plain",
            "application/octet-stream"
    );

    private final long maxFileSizeBytes;

    public MarkdownFileValidator(long maxFileSizeBytes) {
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    public void validate(UploadLessonContentCommand command) {
        if (command == null || command.content() == null || command.content().length == 0) {
            throw new InvalidMarkdownFileException("file must not be empty");
        }

        String filename = command.filename();
        if (filename == null || !(filename.endsWith(".md") || filename.endsWith(".markdown"))) {
            throw new InvalidMarkdownFileException("file must have .md or .markdown extension");
        }

        String contentType = command.contentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new InvalidMarkdownFileException("unsupported content type: " + contentType);
        }

        if (command.sizeBytes() > maxFileSizeBytes) {
            throw new InvalidMarkdownFileException(
                    "file size " + command.sizeBytes() + " exceeds limit " + maxFileSizeBytes);
        }

        try {
            StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(command.content()));
        } catch (CharacterCodingException e) {
            throw new InvalidMarkdownFileException("file content is not valid UTF-8 text", e);
        }
    }
}
