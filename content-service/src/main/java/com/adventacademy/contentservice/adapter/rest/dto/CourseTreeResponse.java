package com.adventacademy.contentservice.adapter.rest.dto;

import com.adventacademy.contentservice.domain.constructor.Component;
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
        List<ComponentNode> components
) {
    public record ComponentNode(
            UUID componentId,
            int position,
            String title,
            PublicationStatus status,
            List<ComponentResponse.LessonResponse> lessons
    ) {
    }

    public static CourseTreeResponse from(Course course, Map<UUID, Component> componentsById) {
        List<ComponentNode> nodes = new ArrayList<>();
        for (ComponentRef ref : course.getComponents()) {
            Component component = componentsById.get(ref.getComponentId());
            if (component == null) {
                continue;
            }
            List<ComponentResponse.LessonResponse> lessons = component.getLessons().stream()
                    .map(l -> new ComponentResponse.LessonResponse(l.getId(), l.getTitle(), l.getPosition()))
                    .toList();
            nodes.add(new ComponentNode(
                    component.getId(), ref.getPosition(), component.getTitle(), component.getStatus(), lessons));
        }
        return new CourseTreeResponse(
                course.getId(), course.getTitle(), course.getDescription(), course.getStatus(), nodes);
    }
}
