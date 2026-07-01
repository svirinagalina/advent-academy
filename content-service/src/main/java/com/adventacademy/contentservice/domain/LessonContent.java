package com.adventacademy.contentservice.domain;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class LessonContent {

    private final UUID id;
    private final UUID lessonId;
    private final String markdown;
    private String html;
    private ContentStatus status;
    private final Instant createdAt;
    private Instant updatedAt;

    private LessonContent(
            UUID id,
            UUID lessonId,
            String markdown,
            String html,
            ContentStatus status,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.lessonId = lessonId;
        this.markdown = markdown;
        this.html = html;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static LessonContent create(UUID lessonId, String markdown) {
        return create(lessonId, markdown, Clock.systemUTC());
    }

    public static LessonContent create(UUID lessonId, String markdown, Clock clock) {
        Objects.requireNonNull(clock, "clock must not be null");
        validateCreation(lessonId, markdown);

        Instant now = clock.instant();
        return new LessonContent(
                UUID.randomUUID(),
                lessonId,
                markdown,
                null,
                ContentStatus.DRAFT,
                now,
                now
        );
    }

    public void changeStatus(ContentStatus target) {
        changeStatus(target, Clock.systemUTC());
    }

    public void changeStatus(ContentStatus target, Clock clock) {
        Objects.requireNonNull(target, "target must not be null");
        Objects.requireNonNull(clock, "clock must not be null");

        if (!status.canTransitionTo(target)) {
            throw new IllegalStateException(
                    String.format("Cannot transition from %s to %s", status, target)
            );
        }

        this.status = target;
        this.updatedAt = clock.instant();
    }

    public void updateContent(String markdown, String html, Clock clock) {
        Objects.requireNonNull(clock, "clock must not be null");
        if (markdown == null || markdown.isBlank()) {
            throw new IllegalArgumentException("markdown must not be blank");
        }

        this.html = html;
        this.updatedAt = clock.instant();
    }

    private static void validateCreation(UUID lessonId, String markdown) {
        if (lessonId == null) {
            throw new IllegalArgumentException("lessonId must not be null");
        }
        if (markdown == null || markdown.isBlank()) {
            throw new IllegalArgumentException("markdown must not be blank");
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getLessonId() {
        return lessonId;
    }

    public String getMarkdown() {
        return markdown;
    }

    public String getHtml() {
        return html;
    }

    public ContentStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}