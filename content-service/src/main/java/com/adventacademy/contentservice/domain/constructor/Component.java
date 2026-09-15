package com.adventacademy.contentservice.domain.constructor;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Aggregate root of the course constructor holding an ordered list of {@link Lesson}s.
 * <p>
 * A component starts in {@link PublicationStatus#DRAFT} with no lessons. Lessons are
 * appended through {@link #addLesson(String, Clock)}, which assigns each lesson a
 * contiguous zero-based {@code position} so ordering is part of the domain model,
 * not a persistence concern.
 */

public final class Component {

    private final UUID id;
    private String title;
    private String description;
    private PublicationStatus status;
    private final UUID ownerId;
    private final List<Lesson> lessons;
    private final Instant createdAt;
    private Instant updatedAt;
    /** Revision counter for optimistic concurrency control; guards against lost updates on concurrent edits. Null until first persisted. */
    private final Long version;

    private Component(UUID id, String title, String description, PublicationStatus status, UUID ownerId,
                      List<Lesson> lessons, Instant createdAt, Instant updatedAt, Long version) {
        this.id = id;
        this.title = title;
        this.status = status;
        this.ownerId = ownerId;
        this.lessons = lessons;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.description = description;
        this.version = version;
    }

    public static Component create(String title, String description, UUID ownerId, Clock clock) {
        Objects.requireNonNull(clock, "clock must not be null");
        Objects.requireNonNull(ownerId, "ownerId must not be null");
        validateTitle(title);

        Instant now = clock.instant();
        return new Component(UUID.randomUUID(), title, description, PublicationStatus.DRAFT, ownerId,
                new ArrayList<>(), now, now, null);
    }

    public static Component restore(UUID id, String title, String description, PublicationStatus status, UUID ownerId,
                                    List<Lesson> lessons, Instant createdAt, Instant updatedAt, Long version) {
        List<Lesson> ordered = new ArrayList<>(lessons == null ? List.of() : lessons);
        ordered.sort(Comparator.comparingInt(Lesson::getPosition));
        return new Component(id, title, description, status, ownerId, ordered, createdAt, updatedAt, version);
    }

    /**
     * Appends a new lesson at the next free position and returns it.
     */
    public Lesson addLesson(String title, Clock clock) {
        requireDraft();
        Objects.requireNonNull(clock, "clock must not be null");
        Lesson lesson = Lesson.create(title, lessons.size(), clock);
        lessons.add(lesson);
        this.updatedAt = clock.instant();
        return lesson;
    }

    public void removeLesson(UUID lessonId, Clock clock) {
        requireDraft();
        Objects.requireNonNull(clock, "clock must not be null");
        boolean removed = lessons.removeIf(lesson -> lesson.getId().equals(lessonId));
        if (!removed) {
            throw new IllegalArgumentException("lesson not found: " + lessonId);
        }
        renumber(clock);
        this.updatedAt = clock.instant();
    }

    public void reorderLessons(List<UUID> orderedIds, Clock clock) {
        requireDraft();
        Objects.requireNonNull(clock, "clock must not be null");
        Objects.requireNonNull(orderedIds, "orderedIds must not be null");

        Map<UUID, Lesson> byId = new HashMap<>();
        for (Lesson lesson : lessons) {
            byId.put(lesson.getId(), lesson);
        }
        if (orderedIds.size() != lessons.size() || !byId.keySet().equals(new HashSet<>(orderedIds))) {
            throw new IllegalArgumentException("orderedIds must be a permutation of current lesson ids");
        }

        lessons.clear();
        for (UUID lessonId : orderedIds) {
            lessons.add(byId.get(lessonId));
        }
        renumber(clock);
        this.updatedAt = clock.instant();
    }

    public void renameLesson(UUID lessonId, String title, Clock clock) {
        requireDraft();
        Objects.requireNonNull(clock, "clock must not be null");
        Lesson lesson = lessons.stream()
                .filter(l -> l.getId().equals(lessonId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("lesson not found: " + lessonId));
        lesson.rename(title, clock);
        this.updatedAt = clock.instant();
    }

    public void rename(String title, String description, Clock clock) {
        requireDraft();
        Objects.requireNonNull(clock, "clock must not be null");
        validateTitle(title);
        this.title = title;
        this.description = description;
        this.updatedAt = clock.instant();
    }

    public void publish(ComponentPublicationPolicy policy, Clock clock) {
        Objects.requireNonNull(policy, "policy must not be null");
        Objects.requireNonNull(clock, "clock must not be null");

        if (lessons.isEmpty()) {
            throw new IllegalStateException("Component must have at least one lesson to be published");
        }
        List<UUID> lessonIds = lessons.stream().map(Lesson::getId).toList();
        Set<UUID> unpublishable = policy.findUnpublishableLessons(lessonIds);
        if (!unpublishable.isEmpty()) {
            throw new ComponentNotReadyForPublicationException(unpublishable);
        }
        changeStatus(PublicationStatus.PUBLISHED, clock);
    }

    public void archive(Clock clock) {
        changeStatus(PublicationStatus.ARCHIVED, clock);
    }

    private void renumber(Clock clock) {
        for (int i = 0; i < lessons.size(); i++) {
            lessons.get(i).moveTo(i, clock);
        }
    }

    private void changeStatus(PublicationStatus target, Clock clock) {
        Objects.requireNonNull(target, "target must not be null");
        Objects.requireNonNull(clock, "clock must not be null");
        if (!status.canTransitionTo(target)) {
            throw new IllegalStateException(
                    String.format("Cannot transition from %s to %s", status, target));
        }
        this.status = target;
        this.updatedAt = clock.instant();
    }

    private static void validateTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
    }

    private void requireDraft() {
        if (status != PublicationStatus.DRAFT) {
            throw new IllegalStateException(
                    String.format("Component %s is %s and cannot be modified", id, status));
        }
    }

    public void ensureDeletable() {
        if (status != PublicationStatus.DRAFT) {
            throw new IllegalStateException(
                    String.format("Component %s is %s and cannot be deleted", id, status));
        }
    }

    public UUID getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public PublicationStatus getStatus() {
        return status;
    }

    /**
     * @return the user who created the component; {@code null} for rows that predate ownership,
     * which stay curatable by {@code ADMIN} and {@code CONTENT_EDITOR} only.
     */
    public UUID getOwnerId() {
        return ownerId;
    }

    /**
     * @return the lessons ordered by position; the returned list is unmodifiable.
     */
    public List<Lesson> getLessons() {
        return List.copyOf(lessons);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getVersion() {
        return version;
    }
}
