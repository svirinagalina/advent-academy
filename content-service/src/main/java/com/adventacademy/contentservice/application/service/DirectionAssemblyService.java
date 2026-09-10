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
 * <p>
 * A direction is part of the constructor, not of the public catalog, so it is authorized against
 * the object: its owner, an {@code ADMIN} and a {@code CONTENT_EDITOR} may read and change it,
 * everybody else only sees the directions they created. Refusals are audited. The public catalog
 * keeps reading directions through {@link GetCatalogService} and is unaffected.
 */
@Service
@Transactional
public class DirectionAssemblyService {

    private static final String AUDIT_EVENT_TYPE = "DIRECTION_ACCESS";
    private static final String RESOURCE_TYPE = "Direction";

    private final DirectionRepository directionRepository;
    private final CourseRepository courseRepository;
    private final ContentAccessGuard accessGuard;
    private final Clock clock;

    public DirectionAssemblyService(DirectionRepository directionRepository,
                                    CourseRepository courseRepository,
                                    ContentAccessGuard accessGuard) {
        this.directionRepository = directionRepository;
        this.courseRepository = courseRepository;
        this.accessGuard = accessGuard;
        this.clock = Clock.systemUTC();
    }

    public Direction create(String title, String description) {
        String safeTitle = PlainTextNormalizer.normalizeTitle(title);
        String safeDescription = PlainTextNormalizer.normalizeDescription(description);
        return directionRepository.save(
                Direction.create(safeTitle, safeDescription, accessGuard.requireCurrentUserId(), clock));
    }

    /**
     * @return every direction for curators, and only the caller's own directions for everybody else.
     */
    @Transactional(readOnly = true)
    public List<Direction> findAll() {
        if (accessGuard.isCurator()) {
            return directionRepository.findAll();
        }
        return directionRepository.findAll().stream()
                .filter(direction -> accessGuard.isOwnedByCurrentUser(direction.getOwnerId()))
                .toList();
    }

    /**
     * @throws ContentAccessDeniedException when the direction belongs to another user
     */
    @Transactional(readOnly = true)
    public Direction get(UUID id) {
        Direction direction = load(id);
        accessGuard.requireCurator(AUDIT_EVENT_TYPE, RESOURCE_TYPE, direction.getId(), direction.getOwnerId(),
                "READ_DIRECTION");
        return direction;
    }

    public Direction rename(UUID id, String title, String description) {
        Direction direction = requireCurator(id, "RENAME_DIRECTION");
        String safeTitle = PlainTextNormalizer.normalizeTitle(title);
        String safeDescription = PlainTextNormalizer.normalizeDescription(description);
        direction.rename(safeTitle, safeDescription, clock);
        return directionRepository.save(direction);
    }

    public void delete(UUID id) {
        Direction direction = requireCurator(id, "DELETE_DIRECTION");
        directionRepository.deleteById(direction.getId());
    }

    public Direction addCourse(UUID directionId, UUID courseId) {
        if (!courseRepository.existsById(courseId)) {
            throw new IllegalArgumentException("course does not exist: " + courseId);
        }
        Direction direction = requireCurator(directionId, "ADD_COURSE");
        direction.addCourse(courseId, clock);
        return directionRepository.save(direction);
    }

    public Direction removeCourse(UUID directionId, UUID courseId) {
        Direction direction = requireCurator(directionId, "REMOVE_COURSE");
        direction.removeCourse(courseId, clock);
        return directionRepository.save(direction);
    }

    public Direction reorderCourses(UUID directionId, List<UUID> orderedIds) {
        Direction direction = requireCurator(directionId, "REORDER_COURSES");
        direction.reorderCourses(orderedIds, clock);
        return directionRepository.save(direction);
    }

    private Direction load(UUID id) {
        return directionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Direction not found: " + id));
    }

    private Direction requireCurator(UUID id, String operation) {
        Direction direction = load(id);
        accessGuard.requireCurator(AUDIT_EVENT_TYPE, RESOURCE_TYPE, direction.getId(), direction.getOwnerId(), operation);
        return direction;
    }
}
