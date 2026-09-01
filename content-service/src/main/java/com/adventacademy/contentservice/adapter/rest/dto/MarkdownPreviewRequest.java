package com.adventacademy.contentservice.adapter.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MarkdownPreviewRequest(
    @NotBlank(message = "Markdown content is required")
    @Size(max = 100000, message = "Markdown content too large (max 100KB)")
    String markdown
) {}
