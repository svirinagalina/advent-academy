package com.adventacademy.contentservice.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Schema mapping; authoring uses JDBC and the parent lesson lock for atomic compare-and-save. */
@Entity
@Table(name = "lesson_drafts")
public class LessonDraftEntity {
    @Id
    @Column(name = "lesson_id")
    private UUID lessonId;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String markdown;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String html;
    @Column(nullable = false)
    private long version;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    public LessonDraftEntity() { }
}
