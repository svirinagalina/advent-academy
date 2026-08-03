package com.adventacademy.contentservice.application.service;

import com.adventacademy.contentservice.application.validation.PlainTextNormalizer;
import com.adventacademy.contentservice.domain.constructor.Course;
import com.adventacademy.contentservice.domain.constructor.CoursePublicationPolicy;
import com.adventacademy.contentservice.domain.repository.ComponentRepository;
import com.adventacademy.contentservice.domain.repository.CourseRepository;
import com.adventacademy.contentservice.domain.repository.DirectionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Assembles courses from published components and drives the course lifecycle.
 */
@Service
@Transactional
public class CourseAssemblyService {

    private final CourseRepository courseRepository;
    private final ComponentRepository componentRepository;
    private final DirectionRepository directionRepository;
    private final CoursePublicationPolicy publicationPolicy;
    private final Clock clock;

    public CourseAssemblyService(CourseRepository courseRepository,
                                 ComponentRepository componentRepository,
                                 DirectionRepository directionRepository,
                                 CoursePublicationPolicy publicationPolicy) {
        this.courseRepository = courseRepository;
        this.componentRepository = componentRepository;
        this.directionRepository = directionRepository;
        this.publicationPolicy = publicationPolicy;
        this.clock = Clock.systemUTC();
    }

    public Course create(String title, String description) {
        String safeTitle = PlainTextNormalizer.normalizeTitle(title);
        String safeDescription = PlainTextNormalizer.normalizeDescription(description);
        return courseRepository.save(Course.create(safeTitle, safeDescription, clock));
    }

    @Transactional(readOnly = true)
    public List<Course> findAll() {
        return courseRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Course get(UUID id) {
        return courseRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Course not found: " + id));
    }

    public Course rename(UUID id, String title, String description) {
        Course course = get(id);
        String safeTitle = PlainTextNormalizer.normalizeTitle(title);
        String safeDescription = PlainTextNormalizer.normalizeDescription(description);
        course.rename(safeTitle, safeDescription, clock);
        return courseRepository.save(course);
    }

    public void delete(UUID id) {
        Course course = get(id);
        if (directionRepository.existsByCourseId(id)) {
            throw new CourseInUseException(id);
        }
        course.ensureDeletable();
        courseRepository.deleteById(id);
    }

    public Course addComponent(UUID courseId, UUID componentId) {
        if (!componentRepository.existsById(componentId)) {
            throw new IllegalArgumentException("component does not exist: " + componentId);
        }
        Course course = get(courseId);
        course.addComponent(componentId, clock);
        return courseRepository.save(course);
    }

    public Course removeComponent(UUID courseId, UUID componentId) {
        Course course = get(courseId);
        course.removeComponent(componentId, clock);
        return courseRepository.save(course);
    }

    public Course reorderComponents(UUID courseId, List<UUID> orderedIds) {
        Course course = get(courseId);
        course.reorderComponents(orderedIds, clock);
        return courseRepository.save(course);
    }

    public Course publish(UUID id) {
        Course course = get(id);
        course.publish(publicationPolicy, clock);
        return courseRepository.save(course);
    }

    public Course archive(UUID id) {
        Course course = get(id);
        course.archive(clock);
        return courseRepository.save(course);
    }
}
