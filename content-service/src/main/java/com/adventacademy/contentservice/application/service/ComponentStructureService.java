package com.adventacademy.contentservice.application.service;

import com.adventacademy.contentservice.application.validation.PlainTextNormalizer;
import com.adventacademy.contentservice.domain.constructor.Component;
import com.adventacademy.contentservice.domain.constructor.PublicationStatus;
import com.adventacademy.contentservice.domain.repository.ComponentRepository;
import com.adventacademy.contentservice.domain.repository.CourseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Structural editing use-cases of a component: create it and shape its ordered
 * lessons (add, rename, reorder, remove) while it is still a draft, plus deletion
 * guarded against components already referenced by a course.
 * <p>
 * Every state-changing operation is authorized against the component itself, not only against
 * the caller's role: a component may be curated by its owner, by an {@code ADMIN} and by a
 * {@code CONTENT_EDITOR}, so an {@code INSTRUCTOR} never edits somebody else's component through
 * a known id. Drafts are invisible to everybody but their owner and those curators, and every
 * refusal is written to the audit trail.
 */
@Service
@Transactional
public class ComponentStructureService {

    private static final String AUDIT_EVENT_TYPE = "COMPONENT_ACCESS";
    private static final String RESOURCE_TYPE = "Component";

    private final ComponentRepository componentRepository;
    private final CourseRepository courseRepository;
    private final ContentAccessGuard accessGuard;
    private final Clock clock;

    public ComponentStructureService(ComponentRepository componentRepository,
                                     CourseRepository courseRepository,
                                     ContentAccessGuard accessGuard) {
        this.componentRepository = componentRepository;
        this.courseRepository = courseRepository;
        this.accessGuard = accessGuard;
        this.clock = Clock.systemUTC();
    }

    public Component create(String title, String description) {
        String safeTitle = PlainTextNormalizer.normalizeTitle(title);
        String safeDescription = PlainTextNormalizer.normalizeDescription(description);
        Component component = Component.create(safeTitle, safeDescription, accessGuard.requireCurrentUserId(), clock);
        return componentRepository.save(component);
    }

    /**
     * @return the components the current user is allowed to see: published and archived ones for
     * everybody, drafts only for their owner and for curators.
     */
    @Transactional(readOnly = true)
    public List<Component> findAll() {
        if (accessGuard.isCurator()) {
            return componentRepository.findAll();
        }
        return componentRepository.findAll().stream()
                .filter(this::isVisible)
                .toList();
    }

    /**
     * Reads a single component, hiding drafts that belong to somebody else.
     *
     * @throws ContentAccessDeniedException when the component is a draft of another user
     */
    @Transactional(readOnly = true)
    public Component get(UUID id) {
        Component component = load(id);
        requireReadable(component);
        return component;
    }

    public Component rename(UUID id, String title, String description) {
        Component component = requireCurator(id, "RENAME_COMPONENT");
        String safeTitle = PlainTextNormalizer.normalizeTitle(title);
        String safeDescription = PlainTextNormalizer.normalizeDescription(description);
        component.rename(safeTitle, safeDescription, clock);
        return componentRepository.save(component);
    }

    public void delete(UUID id) {
        Component component = requireCurator(id, "DELETE_COMPONENT");
        if (courseRepository.existsByComponentId(id)) {
            throw new ComponentInUseException(id);
        }
        component.ensureDeletable();
        componentRepository.deleteById(id);
    }

    public Component addLesson(UUID componentId, String lessonTitle) {
        Component component = requireCurator(componentId, "ADD_LESSON");
        String safeLessonTitle = PlainTextNormalizer.normalizeTitle(lessonTitle);
        component.addLesson(safeLessonTitle, clock);
        return componentRepository.save(component);
    }

    public Component renameLesson(UUID componentId, UUID lessonId, String title) {
        Component component = requireCurator(componentId, "RENAME_LESSON");
        String safeTitle = PlainTextNormalizer.normalizeTitle(title);
        component.renameLesson(lessonId, safeTitle, clock);
        return componentRepository.save(component);
    }

    public Component removeLesson(UUID componentId, UUID lessonId) {
        Component component = requireCurator(componentId, "REMOVE_LESSON");
        component.removeLesson(lessonId, clock);
        return componentRepository.save(component);
    }

    public Component reorderLessons(UUID componentId, List<UUID> orderedIds) {
        Component component = requireCurator(componentId, "REORDER_LESSONS");
        component.reorderLessons(orderedIds, clock);
        return componentRepository.save(component);
    }

    private Component load(UUID id) {
        return componentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Component not found: " + id));
    }

    /**
     * Loads the component and refuses the operation unless the caller owns it or curates content.
     */
    private Component requireCurator(UUID id, String operation) {
        Component component = load(id);
        accessGuard.requireCurator(AUDIT_EVENT_TYPE, RESOURCE_TYPE, component.getId(), component.getOwnerId(), operation);
        return component;
    }

    private void requireReadable(Component component) {
        if (accessGuard.isCurator() || isVisible(component)) {
            return;
        }
        accessGuard.recordDenial(AUDIT_EVENT_TYPE, RESOURCE_TYPE, component.getId(), "READ_COMPONENT",
                "draft component belongs to another user");
        throw new ContentAccessDeniedException(RESOURCE_TYPE, component.getId());
    }

    private boolean isVisible(Component component) {
        return component.getStatus() != PublicationStatus.DRAFT
                || accessGuard.isOwnedByCurrentUser(component.getOwnerId());
    }
}
