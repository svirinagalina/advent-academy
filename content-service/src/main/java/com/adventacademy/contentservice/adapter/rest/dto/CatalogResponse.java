package com.adventacademy.contentservice.adapter.rest.dto;

import com.adventacademy.contentservice.domain.constructor.Course;
import com.adventacademy.contentservice.domain.constructor.CourseRef;
import com.adventacademy.contentservice.domain.constructor.Direction;
import com.adventacademy.contentservice.domain.constructor.PublicationStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Catalog: directions with their published courses, ordered by position.
 * Directions without a single published course are hidden from the catalog.
 */
public record CatalogResponse(List<DirectionNode> directions) {

    public record DirectionNode(
            UUID id,
            @Schema(description = "Plain text. Render with a text-only sink (e.g. textContent), never innerHTML.")
            String title,
            @Schema(description = "Plain text. Render with a text-only sink (e.g. textContent), never innerHTML.")
            String description,
            List<CourseNode> courses
    ) {
    }

    public record CourseNode(
            UUID courseId,
            int position,
            @Schema(description = "Plain text. Render with a text-only sink (e.g. textContent), never innerHTML.")
            String title,
            PublicationStatus status
    ) {
    }

    public static CatalogResponse from(List<Direction> directions, Map<UUID, Course> coursesById) {
        List<DirectionNode> nodes = new ArrayList<>();
        for (Direction direction : directions) {
            List<CourseNode> courses = new ArrayList<>();
            for (CourseRef ref : direction.getCourses()) {
                Course course = coursesById.get(ref.getCourseId());
                if (course == null) {
                    continue;
                }
                courses.add(new CourseNode(
                        course.getId(), ref.getPosition(), course.getTitle(), course.getStatus()));
            }
            if (courses.isEmpty()) {
                continue;
            }
            nodes.add(new DirectionNode(
                    direction.getId(), direction.getTitle(), direction.getDescription(), courses));
        }
        return new CatalogResponse(nodes);
    }
}