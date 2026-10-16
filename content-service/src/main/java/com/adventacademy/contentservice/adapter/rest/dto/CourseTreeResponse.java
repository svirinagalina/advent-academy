package com.adventacademy.contentservice.adapter.rest.dto;

import com.adventacademy.contentservice.domain.constructor.Component;
import com.adventacademy.contentservice.domain.ContentStatus;
import com.adventacademy.contentservice.domain.constructor.ComponentRef;
import com.adventacademy.contentservice.domain.constructor.Course;
import com.adventacademy.contentservice.domain.constructor.PublicationStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Course expanded into a tree: components with their lessons, ordered by position.
 */
public record CourseTreeResponse(
        UUID id,
        String title,
        String description,
        PublicationStatus status,
        List<ComponentNode> components,
        UUID ownerId,
        Long version
) {
    public record ComponentNode(
            UUID componentId,
            UUID ownerId,
            int position,
            String title,
            PublicationStatus status,
            List<LessonNode> lessons
    ) {
    }

    public record LessonNode(UUID id, String title, int position, boolean contentPresent, ContentStatus contentStatus) { }

    public static CourseTreeResponse from(Course course, Map<UUID, Component> componentsById, Map<UUID, ContentStatus> contentStatuses) {
        List<ComponentNode> nodes = new ArrayList<>();
        for (ComponentRef ref : course.getComponents()) {
            Component component = componentsById.get(ref.getComponentId());
            if (component == null) {
                continue;
            }
            List<LessonNode> lessons = component.getLessons().stream()
                    .map(l -> new LessonNode(l.getId(), l.getTitle(), l.getPosition(),
                            contentStatuses.containsKey(l.getId()), contentStatuses.get(l.getId())))
                    .toList();
            nodes.add(new ComponentNode(
                    component.getId(), component.getOwnerId(), ref.getPosition(), component.getTitle(), component.getStatus(), lessons));
        }
        return new CourseTreeResponse(
                course.getId(), course.getTitle(), course.getDescription(), course.getStatus(), nodes, course.getOwnerId(), course.getVersion());
    }
}
