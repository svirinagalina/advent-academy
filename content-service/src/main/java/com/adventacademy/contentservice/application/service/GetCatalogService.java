package com.adventacademy.contentservice.application.service;

import com.adventacademy.contentservice.domain.constructor.*;
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
 * Read side of the constructor: expands a course into a tree of
 * components with their lessons (components are loaded in one query).
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

    public GetCatalogService(CourseRepository courseRepository,
                             ComponentRepository componentRepository, DirectionRepository directionRepository) {
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
        Map<UUID, Course> coursesById = new HashMap<>();
        for (Course course : courseRepository.findAllById(courseIds)) {
            if (course.getStatus() == PublicationStatus.PUBLISHED) {
                coursesById.put(course.getId(), course);
            }
        }
        return new Catalog(directions, coursesById);
    }
}