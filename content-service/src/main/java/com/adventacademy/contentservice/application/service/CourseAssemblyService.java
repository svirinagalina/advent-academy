package com.adventacademy.contentservice.application.service;

import com.adventacademy.contentservice.application.port.AuditTrailPort;
import com.adventacademy.contentservice.application.port.CurrentUserProvider;
import com.adventacademy.contentservice.application.validation.PlainTextNormalizer;
import com.adventacademy.contentservice.domain.constructor.Course;
import com.adventacademy.contentservice.domain.constructor.CoursePublicationPolicy;
import com.adventacademy.contentservice.domain.constructor.PublicationStatus;
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
 * <p>
 * Every state-changing operation is authorized against the course itself, not only against the
 * caller's role: a course may be curated by its owner, by an {@code ADMIN} and by a
 * {@code CONTENT_EDITOR}. An {@code INSTRUCTOR} therefore only ever touches their own courses.
 * Drafts are invisible to everybody but their owner and those curators, so an unfinished course
 * cannot be enumerated through the API. Both decisions are written to the audit trail.
 */
@Service
@Transactional
public class CourseAssemblyService {

    /** Roles that may curate any course, no matter who created it. */
    private static final String[] CURATOR_ROLES = {"ADMIN", "CONTENT_EDITOR"};

    private static final String AUDIT_EVENT_TYPE = "COURSE_ACCESS";
    private static final String AUDIT_RESULT_DENIED = "DENIED";
    private static final String AUDIT_RESULT_SUCCESS = "SUCCESS";

    private final CourseRepository courseRepository;
    private final ComponentRepository componentRepository;
    private final DirectionRepository directionRepository;
    private final CoursePublicationPolicy publicationPolicy;
    private final CurrentUserProvider currentUserProvider;
    private final AuditTrailPort auditTrail;
    private final Clock clock;

    public CourseAssemblyService(CourseRepository courseRepository,
                                 ComponentRepository componentRepository,
                                 DirectionRepository directionRepository,
                                 CoursePublicationPolicy publicationPolicy,
                                 CurrentUserProvider currentUserProvider,
                                 AuditTrailPort auditTrail) {
        this.courseRepository = courseRepository;
        this.componentRepository = componentRepository;
        this.directionRepository = directionRepository;
        this.publicationPolicy = publicationPolicy;
        this.currentUserProvider = currentUserProvider;
        this.auditTrail = auditTrail;
        this.clock = Clock.systemUTC();
    }

    public Course create(String title, String description) {
        String safeTitle = PlainTextNormalizer.normalizeTitle(title);
        String safeDescription = PlainTextNormalizer.normalizeDescription(description);
        return courseRepository.save(Course.create(safeTitle, safeDescription, currentUserProvider.requireCurrentUserId(), clock));
    }

    /**
     * @return the courses the current user is allowed to see: published and archived ones for
     *         everybody, drafts only for their owner and for curators.
     */
    @Transactional(readOnly = true)
    public List<Course> findAll() {
        if (currentUserProvider.hasAnyRole(CURATOR_ROLES)) {
            return courseRepository.findAll();
        }
        UUID currentUserId = currentUserProvider.currentUserId().orElse(null);
        return courseRepository.findAll().stream()
                .filter(course -> isVisibleTo(course, currentUserId))
                .toList();
    }

    /** Courses the caller can manage, including drafts and archived courses. */
    @Transactional(readOnly = true)
    public List<Course> findManaged() {
        return courseRepository.findAll().stream()
                .filter(course -> currentUserProvider.hasAnyRole(CURATOR_ROLES) || isOwnedByCurrentUser(course))
                .toList();
    }

    /**
     * Reads a single course, hiding drafts that belong to somebody else.
     *
     * @throws CourseAccessDeniedException when the course is a draft of another user
     */
    @Transactional(readOnly = true)
    public Course get(UUID id) {
        Course course = load(id);
        requireReadable(course);
        return course;
    }

    public Course rename(UUID id, String title, String description) {
        Course course = load(id);
        requireCurator(course, "RENAME_COURSE");
        String safeTitle = PlainTextNormalizer.normalizeTitle(title);
        String safeDescription = PlainTextNormalizer.normalizeDescription(description);
        course.rename(safeTitle, safeDescription, clock);
        return courseRepository.save(course);
    }

    public void delete(UUID id) {
        Course course = load(id);
        requireCurator(course, "DELETE_COURSE");
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
        Course course = load(courseId);
        requireCurator(course, "ADD_COMPONENT");
        course.addComponent(componentId, clock);
        return courseRepository.save(course);
    }

    public Course removeComponent(UUID courseId, UUID componentId) {
        Course course = load(courseId);
        requireCurator(course, "REMOVE_COMPONENT");
        course.removeComponent(componentId, clock);
        return courseRepository.save(course);
    }

    public Course reorderComponents(UUID courseId, List<UUID> orderedIds) {
        Course course = load(courseId);
        requireCurator(course, "REORDER_COMPONENTS");
        course.reorderComponents(orderedIds, clock);
        return courseRepository.save(course);
    }

    public Course publish(UUID id) {
        Course course = load(id);
        requireCurator(course, "PUBLISH_COURSE");
        course.publish(publicationPolicy, clock);
        Course published = courseRepository.save(course);
        recordSuccess("PUBLISH_COURSE", published.getId());
        return published;
    }

    public Course archive(UUID id) {
        Course course = load(id);
        requireCurator(course, "ARCHIVE_COURSE");
        course.archive(clock);
        Course archived = courseRepository.save(course);
        recordSuccess("ARCHIVE_COURSE", archived.getId());
        return archived;
    }

    private Course load(UUID id) {
        return courseRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Course not found: " + id));
    }

    /**
     * Allows the operation for the owner and for curators, and records the refusal otherwise.
     */
    private void requireCurator(Course course, String operation) {
        if (currentUserProvider.hasAnyRole(CURATOR_ROLES) || isOwnedByCurrentUser(course)) {
            return;
        }
        recordDenial(operation, course.getId(), "course belongs to another user");
        throw new CourseAccessDeniedException(course.getId());
    }

    private void requireReadable(Course course) {
        if (currentUserProvider.hasAnyRole(CURATOR_ROLES)
                || isVisibleTo(course, currentUserProvider.currentUserId().orElse(null))) {
            return;
        }
        recordDenial("READ_COURSE", course.getId(), "draft course belongs to another user");
        throw new CourseAccessDeniedException(course.getId());
    }

    private boolean isVisibleTo(Course course, UUID userId) {
        return course.getStatus() != PublicationStatus.DRAFT
                || (userId != null && userId.equals(course.getOwnerId()));
    }

    private boolean isOwnedByCurrentUser(Course course) {
        return currentUserProvider.currentUserId()
                .map(userId -> userId.equals(course.getOwnerId()))
                .orElse(false);
    }

    /** Successful lifecycle changes are evidence too, not only refusals. */
    private void recordSuccess(String operation, UUID courseId) {
        auditTrail.record(new AuditTrailPort.AuditEvent(
                AUDIT_EVENT_TYPE,
                currentUserProvider.currentUserId().orElse(null),
                operation,
                "course:" + courseId,
                AUDIT_RESULT_SUCCESS,
                null));
    }

    private void recordDenial(String operation, UUID courseId, String reason) {
        auditTrail.record(new AuditTrailPort.AuditEvent(
                AUDIT_EVENT_TYPE,
                currentUserProvider.currentUserId().orElse(null),
                operation,
                "course:" + courseId,
                AUDIT_RESULT_DENIED,
                reason));
    }
}
