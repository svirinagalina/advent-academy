package com.adventacademy.contentservice.infrastructure.persistence.adapter;

import com.adventacademy.contentservice.domain.constructor.CoursePublicationPolicy;
import com.adventacademy.contentservice.domain.constructor.PublicationStatus;
import com.adventacademy.contentservice.infrastructure.persistence.jpa.ComponentJpaRepository;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Answers which components cannot back a course publication: those that
 * do not exist or are not {@link PublicationStatus#PUBLISHED}.
 */
@Component
public class CoursePublicationPolicyAdapter implements CoursePublicationPolicy {

    private final ComponentJpaRepository componentJpaRepository;

    public CoursePublicationPolicyAdapter(ComponentJpaRepository componentJpaRepository) {
        this.componentJpaRepository = componentJpaRepository;
    }

    @Override
    public Set<UUID> findUnpublishedComponents(Collection<UUID> componentIds) {
        if (componentIds.isEmpty()) {
            return Set.of();
        }
        Set<UUID> published = new HashSet<>(
                componentJpaRepository.findPublishedIds(componentIds, PublicationStatus.PUBLISHED));
        Set<UUID> unpublished = new HashSet<>(componentIds);
        unpublished.removeAll(published);
        return unpublished;
    }
}
