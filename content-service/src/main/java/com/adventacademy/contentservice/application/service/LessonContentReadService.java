package com.adventacademy.contentservice.application.service;

import com.adventacademy.contentservice.domain.ContentStatus;
import com.adventacademy.contentservice.domain.LessonContent;
import com.adventacademy.contentservice.domain.repository.ComponentRepository;
import com.adventacademy.contentservice.domain.repository.LessonContentRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Authoring reads use component ownership, independently of enrollment or course publication. */
@Service
@Transactional(readOnly = true)
public class LessonContentReadService {
    private final ComponentRepository components;
    private final LessonContentRepository contents;
    private final ContentAccessGuard guard;
    private final JdbcTemplate jdbc;

    public LessonContentReadService(ComponentRepository components, LessonContentRepository contents,
                                    ContentAccessGuard guard, JdbcTemplate jdbc) {
        this.components = components;
        this.contents = contents;
        this.guard = guard;
        this.jdbc = jdbc;
    }

    public LessonContent get(UUID lessonId) {
        var component = components.findByLessonId(lessonId)
                .orElseThrow(() -> new NotFoundException("Lesson not found"));
        guard.requireCurator("LESSON_CONTENT_ACCESS", "Component", component.getId(),
                component.getOwnerId(), "READ_LESSON_CONTENT");
        return contents.findByLessonId(lessonId)
                .orElseThrow(() -> new NotFoundException("Lesson content not found"));
    }

    /** Status projection only: no Markdown or HTML is loaded for the course tree. */
    public Map<UUID, ContentStatus> statuses(Collection<UUID> lessonIds) {
        if (lessonIds.isEmpty()) { return Map.of(); }
        String placeholders = String.join(",", Collections.nCopies(lessonIds.size(), "?"));
        return jdbc.query("SELECT l.id AS lesson_id,COALESCE(c.status, 'DRAFT') AS status FROM content.lessons l "
                        + "LEFT JOIN content.lesson_contents c ON c.lesson_id=l.id "
                        + "LEFT JOIN content.lesson_drafts d ON d.lesson_id=l.id "
                        + "WHERE (c.id IS NOT NULL OR d.lesson_id IS NOT NULL) AND l.id IN (" + placeholders + ")",
                (rs, row) -> Map.entry(rs.getObject("lesson_id", UUID.class), ContentStatus.valueOf(rs.getString("status"))),
                lessonIds.toArray()).stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
