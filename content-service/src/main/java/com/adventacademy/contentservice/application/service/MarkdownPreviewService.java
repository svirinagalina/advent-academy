package com.adventacademy.contentservice.application.service;

import com.adventacademy.contentservice.application.command.UploadLessonContentCommand;
import com.adventacademy.contentservice.application.port.MarkdownRenderer;
import com.adventacademy.contentservice.application.validation.InvalidMarkdownFileException;
import com.adventacademy.contentservice.application.validation.MarkdownFileValidator;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

/**
 * Use-case: render an uploaded Markdown file to sanitized HTML for preview purposes only.
 * <p>
 * Reuses the same validation and rendering as the real upload pipeline, but never touches a
 * repository: nothing about a preview is persisted, and no lesson context is required.
 */
@Service
public class MarkdownPreviewService {

    private final MarkdownFileValidator markdownFileValidator;
    private final MarkdownRenderer markdownRenderer;

    public MarkdownPreviewService(MarkdownFileValidator markdownFileValidator,
                                   MarkdownRenderer markdownRenderer) {
        this.markdownFileValidator = markdownFileValidator;
        this.markdownRenderer = markdownRenderer;
    }

    public MarkdownPreviewResult preview(UploadLessonContentCommand command) {
        if (command != null && command.filename() != null && command.filename().endsWith(".zip")) {
            throw new InvalidMarkdownFileException("Preview supports only .md or .markdown files");
        }
        markdownFileValidator.validate(command);

        String markdown = new String(command.content(), StandardCharsets.UTF_8);
        String html = markdownRenderer.render(markdown);
        return new MarkdownPreviewResult(markdown, html);
    }
}
