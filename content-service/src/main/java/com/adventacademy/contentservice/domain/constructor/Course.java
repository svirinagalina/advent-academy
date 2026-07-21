package com.adventacademy.contentservice.domain.constructor;

import java.time.Clock;
import java.time.Instant;
import java.util.*;

/**
 * Aggregate root of the course constructor holding an ordered list of {@link ComponentRef}s.
 * <p>
 * A course starts in {@link PublicationStatus#DRAFT} with no components. Components are
 * referenced through {@link #addComponent(UUID, Clock)}, which assigns each reference a
 * contiguous zero-based {@code position} and rejects duplicate components, so ordering
 * and uniqueness are part of the domain model.
 */
public final class Course {

    private final UUID id;
    private String title;
    private String description;
    private PublicationStatus status;
    private final List<ComponentRef> components;
    private final Instant createdAt;
    private Instant updatedAt;
    /** Revision counter for optimistic concurrency control; guards against lost updates on concurrent edits. Null until first persisted. */
    private final Long version;

    private Course(UUID id, String title, String description, PublicationStatus status,
                   List<ComponentRef> components, Instant createdAt, Instant updatedAt, Long version) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.status = status;
        this.components = components;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
    }

    public static Course create(String title, String description, Clock clock) {
        Objects.requireNonNull(clock, "clock must not be null");
        validateTitle(title);

        Instant now = clock.instant();
        return new Course(UUID.randomUUID(), title, description, PublicationStatus.DRAFT,
                new ArrayList<>(), now, now, null);
    }

    public static Course restore(UUID id, String title, String description, PublicationStatus status,
                                 List<ComponentRef> components, Instant createdAt, Instant updatedAt, Long version) {
        List<ComponentRef> ordered = new ArrayList<>(components == null ? List.of() : components);
        ordered.sort(Comparator.comparingInt(ComponentRef::getPosition));
        return new Course(id, title, description, status, ordered, createdAt, updatedAt, version);
    }

    /**
     * Appends a reference to the given component at the next free position and returns it.
     *
     * @throws IllegalArgumentException if the component is already referenced by this course.
     */
    public ComponentRef addComponent(UUID componentId, Clock clock) {
        requireDraft();
        Objects.requireNonNull(clock, "clock must not be null");
        if (componentId == null) {
            throw new IllegalArgumentException("componentId must not be null");
        }
        boolean alreadyReferenced = components.stream()
                .anyMatch(ref -> ref.getComponentId().equals(componentId));
        if (alreadyReferenced) {
            throw new IllegalArgumentException("component already referenced: " + componentId);
        }
        ComponentRef ref = ComponentRef.create(componentId, components.size());
        components.add(ref);
        this.updatedAt = clock.instant();
        return ref;
    }

    public void removeComponent(UUID componentId, Clock clock) {
        requireDraft();
        Objects.requireNonNull(clock, "clock must not be null");
        boolean removed = components.removeIf(ref -> ref.getComponentId().equals(componentId));
        if (!removed) {
            throw new IllegalArgumentException("component not found: " + componentId);
        }
        renumber();
        this.updatedAt = clock.instant();
    }

    public void reorderComponents(List<UUID> orderedIds, Clock clock) {
        requireDraft();
        Objects.requireNonNull(clock, "clock must not be null");
        Objects.requireNonNull(orderedIds, "orderedIds must not be null");

        Set<UUID> currentIds = new HashSet<>();
        for (ComponentRef ref : components) {
            currentIds.add(ref.getComponentId());
        }
        if (orderedIds.size() != components.size() || !currentIds.equals(new HashSet<>(orderedIds))) {
            throw new IllegalArgumentException("orderedIds must be a permutation of current component ids");
        }

        components.clear();
        for (UUID componentId : orderedIds) {
            components.add(ComponentRef.create(componentId, components.size()));
        }
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

    public void publish(CoursePublicationPolicy policy, Clock clock) {
        Objects.requireNonNull(policy, "policy must not be null");
        Objects.requireNonNull(clock, "clock must not be null");

        if (components.isEmpty()) {
            throw new IllegalStateException("Course must have at least one component to be published");
        }
        List<UUID> componentIds = components.stream().map(ComponentRef::getComponentId).toList();
        Set<UUID> unpublished = policy.findUnpublishedComponents(componentIds);
        if (!unpublished.isEmpty()) {
            throw new CourseNotReadyForPublicationException(unpublished);
        }
        changeStatus(PublicationStatus.PUBLISHED, clock);
    }

    public void archive(Clock clock) {
        changeStatus(PublicationStatus.ARCHIVED, clock);
    }

    private void renumber() {
        List<ComponentRef> renumbered = new ArrayList<>();
        for (int i = 0; i < components.size(); i++) {
            renumbered.add(ComponentRef.create(components.get(i).getComponentId(), i));
        }
        components.clear();
        components.addAll(renumbered);
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
                    String.format("Course %s is %s and cannot be modified", id, status));
        }
    }

    public void ensureDeletable() {
        if (status != PublicationStatus.DRAFT) {
            throw new IllegalStateException(
                    String.format("Course %s is %s and cannot be deleted", id, status));
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
     * @return the component references ordered by position; the returned list is unmodifiable.
     */
    public List<ComponentRef> getComponents() {
        return List.copyOf(components);
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
