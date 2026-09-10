package com.adventacademy.contentservice.application.service;

import com.adventacademy.contentservice.application.command.UploadLessonContentCommand;
import com.adventacademy.contentservice.application.port.MarkdownRenderer;
import com.adventacademy.contentservice.application.validation.MarkdownFileValidator;
import com.adventacademy.contentservice.domain.LessonContent;
import com.adventacademy.contentservice.domain.constructor.Component;
import com.adventacademy.contentservice.domain.repository.ComponentRepository;
import com.adventacademy.contentservice.domain.repository.LessonContentRepository;
import com.adventacademy.contentservice.domain.repository.LessonRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.UUID;

/**
 * Use-case: store the Markdown content of a lesson.
 * <p>
 * Accepts either a plain .md file or a .zip bundle (Markdown + assets/), validates
 * it, ensures the target lesson exists, resolves {@code asset:<uuid>} references
 * into downloadable links, renders the result to sanitized HTML and persists a new
 * {@link LessonContent} in DRAFT status, rejecting a second upload for the same lesson.
 * <p>
 * The lesson is part of a component aggregate, so the upload is authorized against that
 * component: only its owner and content curators ({@code ADMIN}, {@code CONTENT_EDITOR}) may
 * push content into it. Refusals are audited.
 */
@Service
public class UploadLessonContentService {

    private static final String AUDIT_EVENT_TYPE = "LESSON_CONTENT_ACCESS";
    private static final String RESOURCE_TYPE = "Component";

    private final MarkdownFileValidator markdownFileValidator;
    private final ZipContentExtractor zipContentExtractor;
    private final AssetLinkResolver assetLinkResolver;
    private final MarkdownRenderer markdownRenderer;
    private final LessonContentRepository repository;
    private final LessonRepository lessonRepository;
    private final ComponentRepository componentRepository;
    private final ContentAccessGuard accessGuard;

    public UploadLessonContentService(MarkdownFileValidator markdownFileValidator,
                                      ZipContentExtractor zipContentExtractor,
                                      AssetLinkResolver assetLinkResolver,
                                      MarkdownRenderer markdownRenderer,
                                      LessonContentRepository repository,
                                      LessonRepository lessonRepository,
                                      ComponentRepository componentRepository,
                                      ContentAccessGuard accessGuard) {
        this.markdownFileValidator = markdownFileValidator;
        this.zipContentExtractor = zipContentExtractor;
        this.assetLinkResolver = assetLinkResolver;
        this.markdownRenderer = markdownRenderer;
        this.repository = repository;
        this.lessonRepository = lessonRepository;
        this.componentRepository = componentRepository;
        this.accessGuard = accessGuard;
    }

    @Transactional
    public LessonContent saveLessonContent(UploadLessonContentCommand command) {
        markdownFileValidator.validate(command);
        if (!lessonRepository.existsById(command.lessonId())) {
            throw new NotFoundException("Lesson not found: " + command.lessonId());
        }
        requireOwnedLesson(command.lessonId());
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

    /**
     * Refuses the upload unless the caller owns the component the lesson belongs to, or curates
     * content. A lesson without a component cannot be authorized and is refused as well.
     */
    private void requireOwnedLesson(UUID lessonId) {
        Component component = componentRepository.findByLessonId(lessonId).orElse(null);
        if (component != null) {
            accessGuard.requireCurator(AUDIT_EVENT_TYPE, RESOURCE_TYPE, component.getId(), component.getOwnerId(),
                    "UPLOAD_LESSON_CONTENT");
            return;
        }
        // A lesson always belongs to a component; if that link is missing there is nothing to
        // authorize against, so only curators may proceed.
        if (accessGuard.isCurator()) {
            return;
        }
        accessGuard.recordDenial(AUDIT_EVENT_TYPE, RESOURCE_TYPE, lessonId, "UPLOAD_LESSON_CONTENT",
                "lesson does not belong to any component");
        throw new ContentAccessDeniedException("Lesson", lessonId);
    }

    private static boolean isZip(String filename) {
        return filename != null && filename.endsWith(".zip");
    }
}