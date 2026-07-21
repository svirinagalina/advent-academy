package com.adventacademy.contentservice.domain.constructor;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Aggregate root of the course constructor holding an ordered list of {@link CourseRef}s.
 * <p>
 * A direction starts with no courses. Courses are
 * referenced through {@link #addCourse(UUID, Clock)}, which assigns each reference a
 * contiguous zero-based {@code position} and rejects duplicate courses, so ordering
 * and uniqueness are part of the domain model.
 * <p>
 * A direction has no publication status: it is visible in the catalog
 * as long as it contains at least one published course.
 */
public final class Direction {

    private final UUID id;
    private String title;
    private String description;
    private final List<CourseRef> courses;
    private final Instant createdAt;
    private Instant updatedAt;
    /** Revision counter for optimistic concurrency control; guards against lost updates on concurrent edits. Null until first persisted. */
    private final Long version;

    private Direction(UUID id, String title, String description,
                      List<CourseRef> courses, Instant createdAt, Instant updatedAt, Long version) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.courses = courses;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
    }

    public static Direction create(String title, String description, Clock clock) {
        Objects.requireNonNull(clock, "clock must not be null");
        validateTitle(title);

        Instant now = clock.instant();
        return new Direction(UUID.randomUUID(), title, description,
                new ArrayList<>(), now, now, null);
    }

    public static Direction restore(UUID id, String title, String description,
                                    List<CourseRef> courses, Instant createdAt, Instant updatedAt, Long version) {
        List<CourseRef> ordered = new ArrayList<>(courses == null ? List.of() : courses);
        ordered.sort(Comparator.comparingInt(CourseRef::getPosition));
        return new Direction(id, title, description, ordered, createdAt, updatedAt, version);
    }

    /**
     * Appends a reference to the given course at the next free position and returns it.
     *
     * @throws IllegalArgumentException if the course is already referenced by this direction.
     */
    public CourseRef addCourse(UUID courseId, Clock clock) {
        Objects.requireNonNull(clock, "clock must not be null");
        if (courseId == null) {
            throw new IllegalArgumentException("courseId must not be null");
        }
        boolean alreadyReferenced = courses.stream()
                .anyMatch(ref -> ref.getCourseId().equals(courseId));
        if (alreadyReferenced) {
            throw new IllegalArgumentException("course already referenced: " + courseId);
        }
        CourseRef ref = CourseRef.create(courseId, courses.size());
        courses.add(ref);
        this.updatedAt = clock.instant();
        return ref;
    }

    public void removeCourse(UUID courseId, Clock clock) {
        Objects.requireNonNull(clock, "clock must not be null");
        boolean removed = courses.removeIf(ref -> ref.getCourseId().equals(courseId));
        if (!removed) {
            throw new IllegalArgumentException("course not found: " + courseId);
        }
        renumber();
        this.updatedAt = clock.instant();
    }

    public void reorderCourses(List<UUID> orderedIds, Clock clock) {
        Objects.requireNonNull(clock, "clock must not be null");
        Objects.requireNonNull(orderedIds, "orderedIds must not be null");

        Set<UUID> currentIds = new HashSet<>();
        for (CourseRef ref : courses) {
            currentIds.add(ref.getCourseId());
        }
        if (orderedIds.size() != courses.size() || !currentIds.equals(new HashSet<>(orderedIds))) {
            throw new IllegalArgumentException("orderedIds must be a permutation of current course ids");
        }

        courses.clear();
        for (UUID courseId : orderedIds) {
            courses.add(CourseRef.create(courseId, courses.size()));
        }
        this.updatedAt = clock.instant();
    }

    public void rename(String title, String description, Clock clock) {
        Objects.requireNonNull(clock, "clock must not be null");
        validateTitle(title);
        this.title = title;
        this.description = description;
        this.updatedAt = clock.instant();
    }

    private void renumber() {
        List<CourseRef> renumbered = new ArrayList<>();
        for (int i = 0; i < courses.size(); i++) {
            renumbered.add(CourseRef.create(courses.get(i).getCourseId(), i));
        }
        courses.clear();
        courses.addAll(renumbered);
    }

    private static void validateTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
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

    /**
     * @return the course references ordered by position; the returned list is unmodifiable.
     */
    public List<CourseRef> getCourses() {
        return List.copyOf(courses);
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