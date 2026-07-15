package com.adventacademy.contentservice.domain.constructor;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * Domain port answering which components are not ready for publication.
 * Implemented in the infrastructure layer with a query against component contents.
 */
public interface CoursePublicationPolicy {
    /**
     * @return ids of components that do not exist or are not   PUBLISHED
     */
    Set<UUID> findUnpublishedComponents(Collection<UUID> componentIds);
}
