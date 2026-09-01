package com.adventacademy.contentservice.infrastructure.file;

import com.adventacademy.contentservice.domain.model.AllowedFileType;
import com.adventacademy.contentservice.domain.validator.FileTypeDetector;
import com.adventacademy.contentservice.domain.validator.FilenameNormalizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

@Slf4j
@Component
public class FileUploadValidator {

    private final long maxFileSize;

    public FileUploadValidator(@Value("${upload.max-file-size:104857600}") long maxFileSize) {
        this.maxFileSize = maxFileSize;
    }

    /**
     * Validate uploaded file
     * Checks:
     * 1. File size
     * 2. Filename safety
     * 3. Magic bytes (detect actual file type)
     * 4. Consistency: detected type vs extension vs MIME
     * 5. Polyglot detection
     * 6. No SVG, HTML, or dangerous formats
     */
    public void validateUpload(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }

        // Check file size
        if (file.getSize() > maxFileSize) {
            log.warn("File too large: {} bytes (max: {})", file.getSize(), maxFileSize);
            throw new IllegalArgumentException("File exceeds maximum size of " + (maxFileSize / 1024 / 1024) + "MB");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isEmpty()) {
            throw new IllegalArgumentException("Filename is required");
        }

        // Validate filename safety
        if (!FilenameNormalizer.isValidPath(originalFilename)) {
            log.warn("Invalid filename path: {}", originalFilename);
            throw new IllegalArgumentException("Filename contains invalid path characters");
        }

        // Normalize and validate filename
        String normalizedFilename;
        try {
            normalizedFilename = FilenameNormalizer.normalize(originalFilename);
        } catch (IllegalArgumentException e) {
            log.warn("Filename validation failed for {}: {}", originalFilename, e.getMessage());
            throw new IllegalArgumentException("Invalid filename: " + e.getMessage());
        }

        // Detect actual file type by magic bytes
        AllowedFileType detectedType;
        try (InputStream inputStream = file.getInputStream()) {
            detectedType = FileTypeDetector.detectType(inputStream);
        }

        if (detectedType == null) {
            log.warn("Could not determine file type for: {}", originalFilename);
            throw new IllegalArgumentException("Could not determine file type. SVG and HTML are not allowed.");
        }

        // Log detected type
        log.debug("Detected file type: {} for {}", detectedType, originalFilename);

        // Validate consistency: detected type vs extension vs MIME
        String clientMimeType = file.getContentType();
        if (!FileTypeDetector.validateConsistency(detectedType, clientMimeType, originalFilename)) {
            log.warn("File consistency check failed. Detected: {}, Extension: {}, MIME: {}",
                    detectedType, FileTypeDetector.extractExtension(originalFilename), clientMimeType);
            throw new IllegalArgumentException(
                    "File type mismatch. Detected: " + detectedType +
                    ", but extension suggests: " + FileTypeDetector.extractExtension(originalFilename) +
                    (clientMimeType != null ? ", MIME: " + clientMimeType : "")
            );
        }

        // Check for polyglot files (binary with embedded HTML/JS)
        byte[] fileBytes = file.getBytes();
        if (FileTypeDetector.isPotentialPolyglot(fileBytes)) {
            log.warn("Potential polyglot file detected: {}", originalFilename);
            throw new IllegalArgumentException("File appears to contain multiple file formats (polyglot)");
        }

        log.info("File validation passed: {} (type: {}, size: {})", originalFilename, detectedType, file.getSize());
    }

    /**
     * Get proper MIME type based on detected file type
     */
    public String getMimeType(AllowedFileType fileType) {
        if (fileType == null) {
            return "application/octet-stream";
        }
        return fileType.getMimeType();
    }
}
