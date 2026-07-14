package com.adventacademy.contentservice.domain.constructor;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain model of a lesson slot inside a {@link Component}.
 * <p>
 * A lesson is an ordered leaf of the constructor: it carries its {@code position}
 * within the owning component as part of the domain model. The actual rendered
 * body of a lesson lives in {@code LessonContent}, keyed by the lesson id.
 * Instances can only be created with a non-blank title and a non-negative position.
 */
public final class Lesson {

    private final UUID id;
    private String title;
    private int position;
    private final Instant createdAt;
    private Instant updatedAt;

    private Lesson(UUID id, String title, int position, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.title = title;
        this.position = position;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Lesson create(String title, int position, Clock clock) {
        Objects.requireNonNull(clock, "clock must not be null");
        validateTitle(title);
        validatePosition(position);

        Instant now = clock.instant();
        return new Lesson(UUID.randomUUID(), title, position, now, now);
    }

    public static Lesson restore(UUID id, String title, int position,
                                 Instant createdAt, Instant updatedAt) {
        return new Lesson(id, title, position, createdAt, updatedAt);
    }

    public void rename(String title, Clock clock) {
        Objects.requireNonNull(clock, "clock must not be null");
        validateTitle(title);
        this.title = title;
        this.updatedAt = clock.instant();
    }

    void moveTo(int position, Clock clock) {
        Objects.requireNonNull(clock, "clock must not be null");
        validatePosition(position);
        this.position = position;
        this.updatedAt = clock.instant();
    }

    private static void validateTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
    }

    private static void validatePosition(int position) {
        if (position < 0) {
            throw new IllegalArgumentException("position must not be negative");
        }
    }

    public UUID getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public int getPosition() {
        return position;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
