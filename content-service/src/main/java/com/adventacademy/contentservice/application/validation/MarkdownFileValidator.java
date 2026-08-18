package com.adventacademy.contentservice.application.validation;

import com.adventacademy.contentservice.application.command.UploadLessonContentCommand;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Validates that an uploaded lesson file is a non-empty, size-bounded document —
 * either a plain UTF-8 Markdown file or a .zip bundle (Markdown + assets), which
 * is unpacked later by {@link com.adventacademy.contentservice.application.service.ZipContentExtractor}.
 * A plain domain-service style validator: it works on the framework-agnostic
 * {@link UploadLessonContentCommand} and carries no Spring Web dependency.
 */
public class MarkdownFileValidator {

    private static final Set<String> ALLOWED_MARKDOWN_CONTENT_TYPES = Set.of(
            "text/markdown",
            "text/x-markdown",
            "text/plain",
            "application/octet-stream"
    );

    private static final Set<String> ALLOWED_ZIP_CONTENT_TYPES = Set.of(
            "application/zip",
            "application/x-zip-compressed",
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
        boolean isZip = filename != null && filename.endsWith(".zip");
        boolean isMarkdown = filename != null && (filename.endsWith(".md") || filename.endsWith(".markdown"));

        if (!isZip && !isMarkdown) {
            throw new InvalidMarkdownFileException("file must have .md, .markdown or .zip extension");
        }

        String contentType = command.contentType();
        Set<String> allowedContentTypes = isZip ? ALLOWED_ZIP_CONTENT_TYPES : ALLOWED_MARKDOWN_CONTENT_TYPES;
        if (contentType == null || !allowedContentTypes.contains(contentType)) {
            throw new InvalidMarkdownFileException("unsupported content type: " + contentType);
        }

        if (command.sizeBytes() > maxFileSizeBytes) {
            throw new InvalidMarkdownFileException(
                    "file size " + command.sizeBytes() + " exceeds limit " + maxFileSizeBytes);
        }

        if (!isZip) {
            try {
                StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(command.content()));
            } catch (CharacterCodingException e) {
                throw new InvalidMarkdownFileException("file content is not valid UTF-8 text", e);
            }
        }
    }
}