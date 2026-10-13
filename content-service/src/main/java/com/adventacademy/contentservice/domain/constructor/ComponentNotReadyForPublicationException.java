package com.adventacademy.contentservice.domain.constructor;

import java.util.Set;
import java.util.UUID;

/**
 * Thrown when a component cannot be published because some of its lessons
 * have no published content.
 */
public class ComponentNotReadyForPublicationException extends RuntimeException {

    private final Set<UUID> lessonIds;

    public ComponentNotReadyForPublicationException(Set<UUID> lessonIds) {
        super("Component is not ready for publication, unpublishable lessons: " + lessonIds);
        this.lessonIds = Set.copyOf(lessonIds);
    }

    public Set<UUID> getLessonIds() {
        return lessonIds;
    }
}