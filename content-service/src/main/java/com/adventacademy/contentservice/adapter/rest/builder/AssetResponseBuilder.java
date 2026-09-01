package com.adventacademy.contentservice.adapter.rest.builder;

import com.adventacademy.contentservice.domain.model.AllowedFileType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class AssetResponseBuilder {

    private static final MediaType APPLICATION_ZIP = new MediaType("application", "zip");

    /**
     * Build response entity with proper headers based on file type
     * Potentially dangerous files (HTML, SVG, XML) are served as attachment
     * Images and PDFs are served as inline
     */
    public <T> ResponseEntity<T> buildAssetResponse(
            T body,
            AllowedFileType fileType,
            String originalFilename) {

        HttpHeaders headers = new HttpHeaders();

        // Set proper Content-Type
        headers.setContentType(getMediaType(fileType));

        // Always add nosniff header
        headers.add("X-Content-Type-Options", "nosniff");

        // For images, serve inline; for risky types, serve as attachment
        if (shouldServeAsAttachment(fileType)) {
            String encodedFilename = encodeFilename(originalFilename);
            headers.setContentDispositionFormData("attachment", encodedFilename);
        } else {
            // Images and safe types served inline
            headers.setContentDisposition(
                    org.springframework.http.ContentDisposition.inline()
                    .filename(originalFilename, StandardCharsets.UTF_8)
                    .build()
            );
        }

        // Cache headers for images (1 hour)
        if (fileType == AllowedFileType.PNG ||
            fileType == AllowedFileType.JPEG ||
            fileType == AllowedFileType.GIF) {
            headers.setCacheControl("public, max-age=3600");
        }

        // PDF can be cached too
        if (fileType == AllowedFileType.PDF) {
            headers.setCacheControl("public, max-age=3600");
        }

        // Prevent CSRF
        headers.add("X-Frame-Options", "DENY");

        return ResponseEntity.ok()
                .headers(headers)
                .body(body);
    }

    private MediaType getMediaType(AllowedFileType fileType) {
        if (fileType == null) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }

        return switch (fileType) {
            case PNG -> MediaType.IMAGE_PNG;
            case JPEG -> MediaType.IMAGE_JPEG;
            case GIF -> MediaType.IMAGE_GIF;
            case PDF -> MediaType.APPLICATION_PDF;
            case ZIP -> APPLICATION_ZIP;
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };
    }

    /**
     * Determine if file should be served as attachment (forces download)
     * vs inline (allows browser to render)
     */
    private boolean shouldServeAsAttachment(AllowedFileType fileType) {
        // Images and PDF can be rendered inline
        return fileType != AllowedFileType.PNG &&
               fileType != AllowedFileType.JPEG &&
               fileType != AllowedFileType.GIF &&
               fileType != AllowedFileType.PDF;
    }

    /**
     * RFC 5987 encode filename for Content-Disposition header
     * Handles special characters safely
     */
    private String encodeFilename(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "file";
        }

        // Remove any dangerous characters
        filename = filename.replaceAll("[^a-zA-Z0-9._-]", "_");

        // If contains non-ASCII, use RFC 5987 encoding
        if (filename.matches(".*[^\\x00-\\x7F].*")) {
            return "UTF-8''" + filename;
        }

        return filename;
    }
}
