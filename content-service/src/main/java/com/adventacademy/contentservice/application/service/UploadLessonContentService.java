package com.adventacademy.contentservice.application.service;

import com.adventacademy.contentservice.application.command.UploadLessonContentCommand;
import com.adventacademy.contentservice.application.port.MarkdownRenderer;
import com.adventacademy.contentservice.application.validation.MarkdownFileValidator;
import com.adventacademy.contentservice.domain.LessonContent;
import com.adventacademy.contentservice.domain.repository.LessonContentRepository;
import com.adventacademy.contentservice.domain.repository.LessonRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;

/**
 * Use-case: store the Markdown content of a lesson.
 * <p>
 * Accepts either a plain .md file or a .zip bundle (Markdown + assets/), validates
 * it, ensures the target lesson exists, resolves {@code asset:<uuid>} references
 * into downloadable links, renders the result to sanitized HTML and persists a new
 * {@link LessonContent} in DRAFT status, rejecting a second upload for the same lesson.
 */
@Service
public class UploadLessonContentService {

    private final MarkdownFileValidator markdownFileValidator;
    private final ZipContentExtractor zipContentExtractor;
    private final AssetLinkResolver assetLinkResolver;
    private final MarkdownRenderer markdownRenderer;
    private final LessonContentRepository repository;
    private final LessonRepository lessonRepository;

    public UploadLessonContentService(MarkdownFileValidator markdownFileValidator,
                                      ZipContentExtractor zipContentExtractor,
                                      AssetLinkResolver assetLinkResolver,
                                      MarkdownRenderer markdownRenderer,
                                      LessonContentRepository repository,
                                      LessonRepository lessonRepository) {
        this.markdownFileValidator = markdownFileValidator;
        this.zipContentExtractor = zipContentExtractor;
        this.assetLinkResolver = assetLinkResolver;
        this.markdownRenderer = markdownRenderer;
        this.repository = repository;
        this.lessonRepository = lessonRepository;
    }

    @Transactional
    public LessonContent saveLessonContent(UploadLessonContentCommand command) {
        markdownFileValidator.validate(command);
        if (!lessonRepository.existsById(command.lessonId())) {
            throw new NotFoundException("Lesson not found: " + command.lessonId());
        }
        repository.findByLessonId(command.lessonId()).ifPresent(existing -> {
            throw new LessonContentAlreadyExistsException(
                    "Lesson content already exists for " + existing.getLessonId());
        });

        String markdown = isZip(command.filename())
                ? zipContentExtractor.extract(command.content(), command.lessonId())
                : new String(command.content(), StandardCharsets.UTF_8);

        markdown = assetLinkResolver.resolve(markdown, command.lessonId());
        String html = markdownRenderer.render(markdown);

        LessonContent content = LessonContent.create(command.lessonId(), markdown);
        content.updateContent(markdown, html, Clock.systemUTC());
        return repository.save(content);
    }

    private static boolean isZip(String filename) {
        return filename != null && filename.endsWith(".zip");
    }
}