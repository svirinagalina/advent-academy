package com.adventacademy.contentservice.domain.constructor;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * Domain port answering which lessons are not ready for publication.
 * Implemented in the infrastructure layer with a query against lesson contents.
 */
public interface ComponentPublicationPolicy {

    /**
     * @return ids of lessons that have no PUBLISHED lesson content
     */
    Set<UUID> findUnpublishableLessons(Collection<UUID> lessonIds);
}