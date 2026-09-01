package com.adventacademy.contentservice.adapter.rest.controller;

import com.adventacademy.contentservice.adapter.rest.dto.MarkdownPreviewRequest;
import com.adventacademy.contentservice.adapter.rest.dto.MarkdownPreviewResponse;
import com.adventacademy.contentservice.domain.validator.MarkdownSanitizer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

@Slf4j
@RestController
@RequestMapping("/api/lessons/{lessonId}/markdown-preview")
@Tag(name = "Markdown Preview", description = "Preview Markdown rendering with XSS protection")
public class MarkdownPreviewController {

    @PostMapping
    @Operation(
            summary = "Preview Markdown with sanitization",
            description = "Render Markdown to HTML with XSS protection and CSP headers"
    )
    @ApiResponse(responseCode = "200", description = "Preview generated")
    @ApiResponse(responseCode = "401", description = "Unauthorized")
    @ApiResponse(responseCode = "400", description = "Invalid markdown")
    public ResponseEntity<MarkdownPreviewResponse> previewMarkdown(
            @PathVariable String lessonId,
            @Valid @RequestBody MarkdownPreviewRequest request) {

        log.debug("Generating markdown preview for lesson: {}", lessonId);

        String markdown = request.markdown();

        // Sanitize HTML (remove scripts, dangerous tags)
        String sanitized = MarkdownSanitizer.sanitizeHtml(markdown);

        // Check if content is still safe
        boolean isSafe = MarkdownSanitizer.isHtmlSafe(sanitized);

        // Get plain text version
        String plainText = MarkdownSanitizer.getPlainText(sanitized);

        MarkdownPreviewResponse response = new MarkdownPreviewResponse(sanitized, plainText, isSafe);

        // Add CSP header to prevent inline scripts
        return ResponseEntity.ok()
                .header("Content-Security-Policy",
                        "default-src 'none'; " +
                        "img-src 'self' data:; " +
                        "style-src 'self' 'unsafe-inline'; " +
                        "script-src 'none'; " +
                        "object-src 'none'; " +
                        "base-uri 'none'; " +
                        "form-action 'none'")
                .header("X-Content-Type-Options", "nosniff")
                .body(response);
    }
}
