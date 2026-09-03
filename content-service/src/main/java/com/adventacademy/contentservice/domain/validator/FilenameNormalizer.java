package com.adventacademy.contentservice.domain.validator;

import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
public class FilenameNormalizer {

    private static final Pattern UNSAFE_PATTERN = Pattern.compile(
            ".*(\\.\\.|\0|;|`|\\||<|>).*",
            Pattern.CASE_INSENSITIVE
    );

    private static final Set<String> RESERVED_NAMES = new HashSet<>();

    static {
        // Windows reserved names
        RESERVED_NAMES.add("CON");
        RESERVED_NAMES.add("PRN");
        RESERVED_NAMES.add("AUX");
        RESERVED_NAMES.add("NUL");
        for (int i = 1; i <= 9; i++) {
            RESERVED_NAMES.add("COM" + i);
            RESERVED_NAMES.add("LPT" + i);
        }
    }

    private static final int MAX_FILENAME_LENGTH = 255;
    private static final int MAX_COMPONENT_LENGTH = 200; // Leave room for UUID prefix

    /**
     * Normalize and validate filename
     * Returns: {UUID}_{sanitized_original_name}
     * Example: 7f3a9e2c-4b1d_lesson_content.png
     */
    public static String normalize(String originalFilename) {
        if (originalFilename == null || originalFilename.trim().isEmpty()) {
            throw new IllegalArgumentException("Filename cannot be empty");
        }

        // Remove path separators
        String filename = originalFilename
                .replace("\\", "_")
                .replace("/", "_");

        // Remove null bytes and other dangerous characters
        filename = filename.replaceAll("[\0\r\n]", "_");

        // Reject the original name: the replacements above would hide null bytes here
        if (UNSAFE_PATTERN.matcher(originalFilename).matches()) {
            log.warn("Rejecting unsafe filename: {}", originalFilename);
            throw new IllegalArgumentException("Filename contains invalid characters");
        }

        // Extract and validate extension
        String extension = FileTypeDetector.extractExtension(filename);
        if (extension == null || extension.isEmpty()) {
            throw new IllegalArgumentException("Filename must have an extension");
        }

        // Extract base name (without extension)
        int lastDot = filename.lastIndexOf('.');
        String baseName = filename.substring(0, lastDot);

        // Sanitize base name: keep only alphanumeric, dash, underscore
        baseName = baseName.replaceAll("[^a-zA-Z0-9_-]", "_");

        // Remove consecutive underscores
        baseName = baseName.replaceAll("_+", "_");

        // Trim to max length
        if (baseName.length() > MAX_COMPONENT_LENGTH) {
            baseName = baseName.substring(0, MAX_COMPONENT_LENGTH);
        }

        // Remove leading/trailing underscores
        baseName = baseName.replaceAll("^_+|_+$", "");

        if (baseName.isEmpty()) {
            baseName = "file";
        }

        // Validate extension
        String ext = extension.toLowerCase();
        if (isReservedName(ext)) {
            throw new IllegalArgumentException("Reserved extension: " + ext);
        }

        // Generate normalized filename with UUID prefix (ensures uniqueness)
        String uuid = UUID.randomUUID().toString().substring(0, 8); // First 8 chars
        String normalized = uuid + "_" + baseName + "." + ext;

        // Final length check
        if (normalized.length() > MAX_FILENAME_LENGTH) {
            // Trim base name further
            int maxBase = MAX_FILENAME_LENGTH - uuid.length() - 2 - ext.length() - 1; // -1 for dot
            if (maxBase > 0) {
                baseName = baseName.substring(0, Math.min(baseName.length(), maxBase));
                normalized = uuid + "_" + baseName + "." + ext;
            }
        }

        log.debug("Normalized filename: {} -> {}", originalFilename, normalized);
        return normalized;
    }

    /**
     * Extract original filename from normalized one (for audit/display)
     * Keeps: {UUID}_{original}
     */
    public static String getDisplayName(String normalizedFilename) {
        if (normalizedFilename == null || !normalizedFilename.contains("_")) {
            return normalizedFilename;
        }
        // Skip UUID and return the rest
        return normalizedFilename.substring(normalizedFilename.indexOf('_') + 1);
    }

    private static boolean isReservedName(String name) {
        return RESERVED_NAMES.contains(name.toUpperCase());
    }

    /**
     * Validate that filename doesn't contain path traversal attempts
     */
    public static boolean isValidPath(String filename) {
        if (filename == null || filename.isEmpty()) {
            return false;
        }

        String normalized = filename.replace("\\", "/");

        // Reject obvious traversal
        if (normalized.contains("..") ||
            normalized.startsWith("/") ||
            normalized.contains("//")) {
            return false;
        }

        return true;
    }
}
