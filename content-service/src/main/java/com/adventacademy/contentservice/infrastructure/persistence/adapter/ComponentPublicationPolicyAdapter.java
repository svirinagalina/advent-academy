package com.adventacademy.contentservice.infrastructure.persistence.adapter;

import com.adventacademy.contentservice.domain.ContentStatus;
import com.adventacademy.contentservice.domain.constructor.ComponentPublicationPolicy;
import com.adventacademy.contentservice.infrastructure.persistence.jpa.LessonContentJpaRepository;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Answers which lessons cannot be published: those that have no lesson content
 * in {@link ContentStatus#PUBLISHED} status.
 */
@Component
public class ComponentPublicationPolicyAdapter implements ComponentPublicationPolicy {

    private final LessonContentJpaRepository lessonContentJpaRepository;

    public ComponentPublicationPolicyAdapter(LessonContentJpaRepository lessonContentJpaRepository) {
        this.lessonContentJpaRepository = lessonContentJpaRepository;
    }

    @Override
    public Set<UUID> findUnpublishableLessons(Collection<UUID> lessonIds) {
        if (lessonIds.isEmpty()) {
            return Set.of();
        }
        Set<UUID> published = new HashSet<>(
                lessonContentJpaRepository.findLessonIdsByLessonIdInAndStatus(lessonIds, ContentStatus.PUBLISHED));
        Set<UUID> unpublishable = new HashSet<>(lessonIds);
        unpublishable.removeAll(published);
        return unpublishable;
    }
}
