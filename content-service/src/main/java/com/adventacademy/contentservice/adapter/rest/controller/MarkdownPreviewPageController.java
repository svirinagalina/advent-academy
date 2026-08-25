package com.adventacademy.contentservice.adapter.rest.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * Redirects bare requests for the Markdown preview page to its actual static asset.
 * <p>
 * Spring Boot only auto-serves {@code index.html} for the context root, not for a nested static
 * directory like {@code /markdown-preview/}, so without this the directory URL 404s.
 */
@RestController
public class MarkdownPreviewPageController {

    @GetMapping({"/markdown-preview", "/markdown-preview/"})
    public ResponseEntity<Void> redirectToIndex() {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create("/markdown-preview/index.html"))
                .build();
    }
}
