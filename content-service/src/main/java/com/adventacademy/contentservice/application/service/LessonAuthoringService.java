package com.adventacademy.contentservice.application.service;

import com.adventacademy.contentservice.application.command.UploadLessonContentCommand;
import com.adventacademy.contentservice.application.port.MarkdownRenderer;
import com.adventacademy.contentservice.application.validation.MarkdownFileValidator;
import com.adventacademy.contentservice.domain.repository.ComponentRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Draft revisions are independent of the existing learner snapshot, including legacy imports. */
@Service
@Transactional
public class LessonAuthoringService {
    private final JdbcTemplate jdbc;
    private final ComponentRepository components;
    private final ContentAccessGuard guard;
    private final MarkdownFileValidator validator;
    private final AssetLinkResolver assets;
    private final MarkdownRenderer renderer;

    public LessonAuthoringService(JdbcTemplate jdbc, ComponentRepository components, ContentAccessGuard guard,
                                  MarkdownFileValidator validator, AssetLinkResolver assets, MarkdownRenderer renderer) {
        this.jdbc = jdbc;
        this.components = components;
        this.guard = guard;
        this.validator = validator;
        this.assets = assets;
        this.renderer = renderer;
    }

    public record Revision(String markdown, String html, long version, boolean published) { }

    public void authorize(UUID lessonId, String action) {
        var component = components.findByLessonId(lessonId).orElseThrow(() -> new NotFoundException("Lesson not found"));
        guard.requireCurator("LESSON_CONTENT_ACCESS", "Component", component.getId(), component.getOwnerId(), action);
    }

    @Transactional(readOnly = true)
    public Revision get(UUID lessonId) {
        authorize(lessonId, "READ_DRAFT");
        return current(lessonId);
    }

    private Revision current(UUID lessonId) {
        var drafts = jdbc.query("SELECT markdown,html,version FROM content.lesson_drafts WHERE lesson_id=?",
                (rs, n) -> new Revision(rs.getString(1), rs.getString(2), rs.getLong(3), false), lessonId);
        boolean published = Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM content.lesson_contents WHERE lesson_id=? AND status='PUBLISHED')",
                Boolean.class, lessonId));
        if (!drafts.isEmpty()) {
            var draft = drafts.getFirst();
            return new Revision(draft.markdown(), draft.html(), draft.version(), published);
        }
        return jdbc.query("SELECT markdown,html FROM content.lesson_contents WHERE lesson_id=?",
                (rs, n) -> new Revision(rs.getString(1), rs.getString(2), 1, published), lessonId)
                .stream().findFirst().orElse(new Revision("", "", 0, false));
    }

    private void lock(UUID lessonId) {
        jdbc.queryForObject("SELECT id FROM content.lessons WHERE id=? FOR UPDATE", UUID.class, lessonId);
    }

    private void checkVersion(Revision current, long expected) {
        if (current.version() != expected) {
            throw new IllegalStateException("Content changed in another editor. Reopen the lesson before saving again.");
        }
    }

    @Transactional(readOnly = true)
    public Revision preview(UUID lessonId, String markdown) {
        authorize(lessonId, "PREVIEW_DRAFT");
        return new Revision(markdown, render(lessonId, markdown), 0, false);
    }

    private String render(UUID lessonId, String markdown) {
        if (markdown == null) { throw new IllegalArgumentException("markdown is required"); }
        // Blank drafts are useful while authoring; publication requires real content.
        if (!markdown.isEmpty()) {
            validator.validate(new UploadLessonContentCommand(lessonId, "lesson.md", "text/markdown",
                    markdown.getBytes(StandardCharsets.UTF_8)));
        }
        if (markdown.isBlank()) { return ""; }
        return renderer.render(assets.resolve(markdown, lessonId));
    }

    public Revision save(UUID lessonId, String markdown, long expected) {
        authorize(lessonId, "SAVE_DRAFT");
        lock(lessonId);
        var previous = current(lessonId);
        checkVersion(previous, expected);
        String html = render(lessonId, markdown);
        jdbc.update("INSERT INTO content.lesson_drafts(lesson_id,markdown,html,version,updated_at) VALUES (?,?,?,?,CURRENT_TIMESTAMP) "
                        + "ON CONFLICT (lesson_id) DO UPDATE SET markdown=EXCLUDED.markdown,html=EXCLUDED.html,"
                        + "version=EXCLUDED.version,updated_at=CURRENT_TIMESTAMP",
                lessonId, markdown, html, expected + 1);
        guard.recordSuccess("LESSON_CONTENT_ACCESS", "Lesson", lessonId, "SAVE_DRAFT");
        return new Revision(markdown, html, expected + 1, previous.published());
    }

    public Revision publish(UUID lessonId, long expected) {
        authorize(lessonId, "PUBLISH_DRAFT");
        lock(lessonId);
        var draft = current(lessonId);
        checkVersion(draft, expected);
        if (draft.markdown().isBlank()) { throw new IllegalArgumentException("Cannot publish empty content"); }
        String html = render(lessonId, draft.markdown());
        jdbc.update("INSERT INTO content.lesson_contents(id,lesson_id,markdown,html,status,created_at,updated_at) "
                        + "VALUES (?,?,?,?,'PUBLISHED',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP) ON CONFLICT (lesson_id) "
                        + "DO UPDATE SET markdown=EXCLUDED.markdown,html=EXCLUDED.html,status='PUBLISHED',updated_at=CURRENT_TIMESTAMP",
                UUID.randomUUID(), lessonId, draft.markdown(), html);
        // Advance the revision on publication too, preventing an older editor from publishing over it.
        jdbc.update("INSERT INTO content.lesson_drafts(lesson_id,markdown,html,version,updated_at) VALUES (?,?,?,?,CURRENT_TIMESTAMP) "
                        + "ON CONFLICT (lesson_id) DO UPDATE SET html=EXCLUDED.html,version=EXCLUDED.version,updated_at=CURRENT_TIMESTAMP",
                lessonId, draft.markdown(), html, expected + 1);
        guard.recordSuccess("LESSON_CONTENT_ACCESS", "Lesson", lessonId, "PUBLISH_DRAFT");
        return new Revision(draft.markdown(), html, expected + 1, true);
    }
}
