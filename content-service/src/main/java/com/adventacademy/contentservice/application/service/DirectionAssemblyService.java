package com.adventacademy.contentservice.application.service;

import com.adventacademy.contentservice.application.validation.PlainTextNormalizer;
import com.adventacademy.contentservice.domain.constructor.Direction;
import com.adventacademy.contentservice.domain.repository.CourseRepository;
import com.adventacademy.contentservice.domain.repository.DirectionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Assembles directions from courses.
 */
@Service
@Transactional
public class DirectionAssemblyService {

    private final DirectionRepository directionRepository;
    private final CourseRepository courseRepository;
    private final Clock clock;

    public DirectionAssemblyService(DirectionRepository directionRepository,
                                    CourseRepository courseRepository) {
        this.directionRepository = directionRepository;
        this.courseRepository = courseRepository;
        this.clock = Clock.systemUTC();
    }

    public Direction create(String title, String description) {
        String safeTitle = PlainTextNormalizer.normalizeTitle(title);
        String safeDescription = PlainTextNormalizer.normalizeDescription(description);
        return directionRepository.save(Direction.create(safeTitle, safeDescription, clock));
    }

    @Transactional(readOnly = true)
    public List<Direction> findAll() {
        return directionRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Direction get(UUID id) {
        return directionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Direction not found: " + id));
    }

    public Direction rename(UUID id, String title, String description) {
        Direction direction = get(id);
        String safeTitle = PlainTextNormalizer.normalizeTitle(title);
        String safeDescription = PlainTextNormalizer.normalizeDescription(description);
        direction.rename(safeTitle, safeDescription, clock);
        return directionRepository.save(direction);
    }

    public void delete(UUID id) {
        get(id);
        directionRepository.deleteById(id);
    }

    public Direction addCourse(UUID directionId, UUID courseId) {
        if (!courseRepository.existsById(courseId)) {
            throw new IllegalArgumentException("course does not exist: " + courseId);
        }
        Direction direction = get(directionId);
        direction.addCourse(courseId, clock);
        return directionRepository.save(direction);
    }

    public Direction removeCourse(UUID directionId, UUID courseId) {
        Direction direction = get(directionId);
        direction.removeCourse(courseId, clock);
        return directionRepository.save(direction);
    }

    public Direction reorderCourses(UUID directionId, List<UUID> orderedIds) {
        Direction direction = get(directionId);
        direction.reorderCourses(orderedIds, clock);
        return directionRepository.save(direction);
    }
}
