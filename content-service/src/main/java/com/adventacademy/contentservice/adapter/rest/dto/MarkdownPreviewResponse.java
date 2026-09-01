package com.adventacademy.contentservice.adapter.rest.dto;

public record MarkdownPreviewResponse(
    String html,
    String plainText,
    boolean safe
) {}
