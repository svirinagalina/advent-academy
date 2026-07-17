package com.adventacademy.contentservice.application.service;

import com.adventacademy.contentservice.domain.constructor.Component;
import com.adventacademy.contentservice.domain.constructor.ComponentRef;
import com.adventacademy.contentservice.domain.constructor.Course;
import com.adventacademy.contentservice.domain.repository.ComponentRepository;
import com.adventacademy.contentservice.domain.repository.CourseRepository;
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

    private final CourseRepository courseRepository;
    private final ComponentRepository componentRepository;

    public GetCatalogService(CourseRepository courseRepository,
                             ComponentRepository componentRepository) {
        this.courseRepository = courseRepository;
        this.componentRepository = componentRepository;
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
}
