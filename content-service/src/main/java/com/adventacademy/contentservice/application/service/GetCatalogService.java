package com.adventacademy.contentservice.application.service;

import com.adventacademy.contentservice.domain.constructor.Component;
import com.adventacademy.contentservice.domain.constructor.ComponentRef;
import com.adventacademy.contentservice.domain.constructor.Course;
import com.adventacademy.contentservice.domain.constructor.CourseRef;
import com.adventacademy.contentservice.domain.constructor.Direction;
import com.adventacademy.contentservice.domain.constructor.PublicationStatus;
import com.adventacademy.contentservice.domain.repository.ComponentRepository;
import com.adventacademy.contentservice.domain.repository.CourseRepository;
import com.adventacademy.contentservice.domain.repository.DirectionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Read side of the constructor.
 * <p>
 * {@code getCourseTree} expands a course into a tree of components with their
 * lessons (components are loaded in one query). {@code getCatalog} returns all
 * directions together with their PUBLISHED courses only — draft and archived
 * courses are filtered out here, so the catalog never exposes unpublished content.
 */
@Service
@Transactional(readOnly = true)
public class GetCatalogService {

    public record CourseTree(Course course, Map<UUID, Component> componentsById) {
    }

    public record Catalog(List<Direction> directions, Map<UUID, Course> coursesById) {
    }

    private final CourseRepository courseRepository;
    private final ComponentRepository componentRepository;
    private final DirectionRepository directionRepository;
    private final ContentAccessGuard guard;

    public GetCatalogService(CourseRepository courseRepository,
                             ComponentRepository componentRepository,
                             DirectionRepository directionRepository, ContentAccessGuard guard) {
        this.guard = guard;
        this.courseRepository = courseRepository;
        this.componentRepository = componentRepository;
        this.directionRepository = directionRepository;
    }

    public CourseTree getCourseTree(UUID courseId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new NotFoundException("Course not found: " + courseId));
        List<UUID> componentIds = course.getComponents().stream()
                .map(ComponentRef::getComponentId)
                .toList();
        Map<UUID, Component> componentsById = new HashMap<>();
        for (Component component : componentRepository.findAllById(componentIds)) {
            if (component.getStatus() == PublicationStatus.DRAFT) {
                guard.requireCurator("COMPONENT_ACCESS", "Component", component.getId(), component.getOwnerId(), "READ_COURSE_TREE");
            }
            componentsById.put(component.getId(), component);
        }
        return new CourseTree(course, componentsById);
    }

    public Catalog getCatalog() {
        List<Direction> directions = directionRepository.findAll();
        List<UUID> courseIds = directions.stream()
                .flatMap(direction -> direction.getCourses().stream())
                .map(CourseRef::getCourseId)
                .distinct()
                .toList();
        Map<UUID, Course> publishedById = new HashMap<>();
        for (Course course : courseRepository.findAllById(courseIds)) {
            if (course.getStatus() == PublicationStatus.PUBLISHED) {
                publishedById.put(course.getId(), course);
            }
        }
        return new Catalog(directions, publishedById);
    }
}