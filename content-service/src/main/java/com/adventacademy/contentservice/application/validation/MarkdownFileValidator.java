package com.adventacademy.contentservice.application.validation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

@Component
public class MarkdownFileValidator {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "text/markdown",
            "text/x-markdown",
            "text/plain",
            "application/octet-stream"
    );

    private final long maxFileSizeBytes;

    public MarkdownFileValidator(@Value("${content.upload.max-file-size-bytes}") long maxFileSizeBytes) {
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    public void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidMarkdownFileException("file must not be empty");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || !(filename.endsWith(".md") || filename.endsWith(".markdown"))) {
            throw new InvalidMarkdownFileException("file must have .md or .markdown extension");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new InvalidMarkdownFileException("unsupported content type: " + contentType);
        }
        if (file.getSize() > maxFileSizeBytes) {
            throw new InvalidMarkdownFileException("file size " + file.getSize() + " exceeds limit " + maxFileSizeBytes);
        }
        try {
            StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(file.getBytes()));
        } catch (
                CharacterCodingException e) {
            throw new InvalidMarkdownFileException("file content is not valid UTF-8 text", e);
        } catch (
                IOException e) {
            throw new InvalidMarkdownFileException("cannot read file content", e);
        }
    }
}