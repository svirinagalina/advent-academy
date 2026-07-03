package com.adventacademy.contentservice.infrastructure.persistence.entity;

import com.adventacademy.contentservice.domain.ContentStatus;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;
@Entity
@Table(name = "lesson_contents")
public class LessonContentEntity {
    @Id
    private UUID id;

    @Column(name = "lesson_id", nullable = false, unique = true)
    private UUID lessonId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String markdown;

    @Column(columnDefinition = "TEXT")
    private String html;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ContentStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public LessonContentEntity() {
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

    public void setId(UUID id) {
        this.id = id;
    }

    public void setLessonId(UUID lessonId) {
        this.lessonId = lessonId;
    }

    public void setMarkdown(String markdown) {
        this.markdown = markdown;
    }

    public void setHtml(String html) {
        this.html = html;
    }

    public void setStatus(ContentStatus status) {
        this.status = status;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
