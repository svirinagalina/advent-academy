package com.adventacademy.contentservice.domain.validator;

import com.adventacademy.contentservice.domain.model.AllowedFileType;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

@Slf4j
public class FileTypeDetector {

    // Magic byte signatures (first bytes of file)
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 0x50, 0x4E, 0x47};
    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] GIF87_MAGIC = {'G', 'I', 'F', '8', '7', 'a'};
    private static final byte[] GIF89_MAGIC = {'G', 'I', 'F', '8', '9', 'a'};
    private static final byte[] PDF_MAGIC = {'%', 'P', 'D', 'F'};
    private static final byte[] ZIP_MAGIC = {0x50, 0x4B, 0x03, 0x04};
    private static final byte[] ZIP_EMPTY_MAGIC = {0x50, 0x4B, 0x05, 0x06};

    // SVG is XML-based text
    private static final String SVG_MAGIC = "<?xml";
    private static final String SVG_TAG = "<svg";

    // HTML signatures
    private static final String HTML_MAGIC = "<!doctype";
    private static final String HTML_TAG = "<html";

    private static final int MAX_MAGIC_BYTES = 512;

    public static AllowedFileType detectType(InputStream inputStream) throws IOException {
        byte[] buffer = new byte[MAX_MAGIC_BYTES];
        int bytesRead = inputStream.read(buffer);

        if (bytesRead < 4) {
            log.warn("File too small to determine type (< 4 bytes)");
            return null;
        }

        // Check magic bytes
        if (startsWith(buffer, PNG_MAGIC)) {
            return AllowedFileType.PNG;
        }
        if (startsWith(buffer, JPEG_MAGIC)) {
            return AllowedFileType.JPEG;
        }
        if (startsWith(buffer, GIF87_MAGIC) || startsWith(buffer, GIF89_MAGIC)) {
            return AllowedFileType.GIF;
        }
        if (startsWith(buffer, PDF_MAGIC)) {
            return AllowedFileType.PDF;
        }
        if (startsWith(buffer, ZIP_MAGIC) || startsWith(buffer, ZIP_EMPTY_MAGIC)) {
            return AllowedFileType.ZIP;
        }

        // Check for dangerous text-based formats
        String textContent = new String(buffer, 0, Math.min(bytesRead, 256)).toLowerCase().trim();

        if (textContent.startsWith(SVG_MAGIC) || textContent.contains(SVG_TAG)) {
            log.warn("Detected SVG file (XML-based)");
            return null; // SVG not allowed
        }

        if (textContent.startsWith(HTML_MAGIC) || textContent.contains(HTML_TAG)) {
            log.warn("Detected HTML file");
            return null; // HTML not allowed
        }

        log.warn("Unknown file type detected");
        return null;
    }

    public static boolean validateConsistency(AllowedFileType detectedType,
                                              String clientMimeType,
                                              String filename) {
        if (detectedType == null) {
            log.warn("Detected type is null");
            return false;
        }

        // Extract extension from filename
        String extension = extractExtension(filename);
        if (extension == null || extension.isEmpty()) {
            log.warn("No file extension found");
            return false;
        }

        // Validate extension matches detected type
        if (!detectedType.matchesExtension(extension)) {
            log.warn("Extension {} does not match detected type {}", extension, detectedType);
            return false;
        }

        // Validate MIME type if provided
        if (clientMimeType != null && !clientMimeType.isEmpty()) {
            if (!detectedType.getMimeType().equalsIgnoreCase(clientMimeType)) {
                log.warn("MIME type {} does not match detected type {}", clientMimeType, detectedType);
                return false;
            }
        }

        log.debug("File consistency validated: {} (ext={}, mime={})", detectedType, extension, clientMimeType);
        return true;
    }

    public static String extractExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            return null;
        }
        int lastDot = filename.lastIndexOf('.');
        if (lastDot > 0 && lastDot < filename.length() - 1) {
            return filename.substring(lastDot + 1);
        }
        return null;
    }

    private static boolean startsWith(byte[] buffer, byte[] pattern) {
        if (buffer.length < pattern.length) {
            return false;
        }
        for (int i = 0; i < pattern.length; i++) {
            if (buffer[i] != pattern[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Check if content appears to be polyglot (multiple valid file types)
     * Example: valid PNG with embedded HTML
     */
    public static boolean isPotentialPolyglot(byte[] fileContent) {
        String textContent = new String(fileContent, 0, Math.min(fileContent.length, 512)).toLowerCase();

        // Check for suspicious HTML-like content in binary files
        if (textContent.contains("<!doctype") ||
            textContent.contains("<html") ||
            textContent.contains("<script") ||
            textContent.contains("javascript:")) {
            log.warn("Potential polyglot file detected (HTML content in binary file)");
            return true;
        }

        return false;
    }
}
