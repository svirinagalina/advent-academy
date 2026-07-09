package com.adventacademy.contentservice.application.service;

import com.adventacademy.contentservice.application.port.MarkdownRenderer;
import com.adventacademy.contentservice.application.validation.InvalidMarkdownFileException;
import com.adventacademy.contentservice.application.validation.MarkdownFileValidator;
import com.adventacademy.contentservice.domain.LessonContent;
import com.adventacademy.contentservice.domain.repository.LessonContentRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.UUID;


@Service
public class UploadLessonContentService {
    private final MarkdownFileValidator markdownFileValidator;
    private final MarkdownRenderer markdownRenderer;
    private final LessonContentRepository repository;

    public UploadLessonContentService(MarkdownFileValidator markdownFileValidator, MarkdownRenderer markdownRenderer, LessonContentRepository repository) {
        this.markdownFileValidator = markdownFileValidator;
        this.markdownRenderer = markdownRenderer;
        this.repository = repository;
    }

    public LessonContent saveLessonContent(UUID lessonId, MultipartFile file) {
        markdownFileValidator.validate(file);
        repository.findByLessonId(lessonId).ifPresent(lessonContent -> {
            throw new LessonContentAlreadyExistsException(
                    "Lesson content already exists for " + lessonContent.getLessonId()
            );
        });
        final String markdown;
        String html = null;
        try {
            markdown = new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new InvalidMarkdownFileException("Cannot read file content", e);

        }
        html = markdownRenderer.render(markdown);
        LessonContent content = LessonContent.create(lessonId, markdown);
        content.updateContent(markdown, html, Clock.systemUTC());
        return repository.save(content);
    }



}
