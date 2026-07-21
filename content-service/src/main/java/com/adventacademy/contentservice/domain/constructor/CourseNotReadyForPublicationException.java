package com.adventacademy.contentservice.domain.constructor;

import java.util.Set;
import java.util.UUID;

/**
 * Thrown when a course cannot be published because some of its components
 * have no published content.
 */
public class CourseNotReadyForPublicationException extends RuntimeException {

    private final Set<UUID> componentIds;


    public CourseNotReadyForPublicationException(Set<UUID> componentIds) {
        super("Course is not ready for publication, unpublishable content: " + componentIds);
        this.componentIds = Set.copyOf(componentIds);
    }

    public Set<UUID> getComponentIds() {
        return componentIds;
    }
}
