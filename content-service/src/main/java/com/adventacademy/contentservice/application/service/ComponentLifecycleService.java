package com.adventacademy.contentservice.application.service;

import com.adventacademy.contentservice.domain.LessonContent;
import com.adventacademy.contentservice.domain.constructor.Component;
import com.adventacademy.contentservice.domain.constructor.ComponentPublicationPolicy;
import com.adventacademy.contentservice.domain.constructor.Course;
import com.adventacademy.contentservice.domain.constructor.Lesson;
import com.adventacademy.contentservice.domain.repository.ComponentRepository;
import com.adventacademy.contentservice.domain.repository.CourseRepository;
import com.adventacademy.contentservice.domain.repository.LessonContentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/**
 * Lifecycle use-cases of a component: publish, archive, clone and promote.
 * <p>
 * Each of them changes or copies a concrete component, so the caller must own it or curate
 * content ({@code ADMIN}, {@code CONTENT_EDITOR}); the copies produced by clone and promote
 * belong to the caller. Successful publications and refusals are both audited.
 */
@Service
@Transactional
public class ComponentLifecycleService {

    private static final String AUDIT_EVENT_TYPE = "COMPONENT_ACCESS";
    private static final String RESOURCE_TYPE = "Component";

    private final ComponentRepository componentRepository;
    private final CourseRepository courseRepository;
    private final LessonContentRepository lessonContentRepository;
    private final ComponentPublicationPolicy publicationPolicy;
    private final ContentAccessGuard accessGuard;
    private final Clock clock;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    public ComponentLifecycleService(ComponentRepository componentRepository,
                                     CourseRepository courseRepository,
                                     LessonContentRepository lessonContentRepository,
                                     ComponentPublicationPolicy publicationPolicy,
                                     ContentAccessGuard accessGuard, org.springframework.jdbc.core.JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.componentRepository = componentRepository;
        this.courseRepository = courseRepository;
        this.lessonContentRepository = lessonContentRepository;
        this.publicationPolicy = publicationPolicy;
        this.accessGuard = accessGuard;
        this.clock = Clock.systemUTC();
    }

    public Component publish(UUID id) {
        Component component = requireCurator(id, "PUBLISH_COMPONENT");
        component.publish(publicationPolicy, clock);
        Component published = componentRepository.save(component);
        accessGuard.recordSuccess(AUDIT_EVENT_TYPE, RESOURCE_TYPE, published.getId(), "PUBLISH_COMPONENT");
        return published;
    }

    public Component archive(UUID id) {
        jdbc.queryForList("SELECT id FROM content.components WHERE id=? FOR UPDATE", id);
        Component component = requireCurator(id, "ARCHIVE_COMPONENT");
        if (Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM content.course_components cc "
                + "JOIN content.courses c ON c.id=cc.course_id WHERE cc.component_id=? AND c.status='PUBLISHED')",
                Boolean.class, id))) { throw new ComponentInUseException(id); }
        component.archive(clock);
        Component archived = componentRepository.save(component);
        accessGuard.recordSuccess(AUDIT_EVENT_TYPE, RESOURCE_TYPE, archived.getId(), "ARCHIVE_COMPONENT");
        return archived;
    }

    /**
     * Deep copy of a component: new component and lesson ids, DRAFT status, owned by the caller.
     * Lesson content of the original lessons is copied as DRAFT for the new lessons.
     */
    public Component clone(UUID id) {
        Component original = requireCurator(id, "CLONE_COMPONENT");
        Component copy = Component.create(original.getTitle(), original.getDescription(),
                accessGuard.requireCurrentUserId(), clock);
        for (Lesson lesson : original.getLessons()) {
            Lesson newLesson = copy.addLesson(lesson.getTitle(), clock);
            lessonContentRepository.findByLessonId(lesson.getId())
                    .ifPresent(content -> {
                        LessonContent contentCopy = LessonContent.create(newLesson.getId(), content.getMarkdown(), clock);
                        contentCopy.updateContent(content.getMarkdown(), content.getHtml(), clock);
                        lessonContentRepository.save(contentCopy);
                    });
        }
        return componentRepository.save(copy);
    }

    /**
     * Wraps the component into a new DRAFT course referencing it (no copying).
     */
    public Course promote(UUID id) {
        Component component = requireCurator(id, "PROMOTE_COMPONENT");
        Course course = Course.create(component.getTitle(), component.getDescription(),
                accessGuard.requireCurrentUserId(), clock);
        course.addComponent(component.getId(), clock);
        return courseRepository.save(course);
    }

    private Component requireCurator(UUID id, String operation) {
        Component component = componentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Component not found: " + id));
        accessGuard.requireCurator(AUDIT_EVENT_TYPE, RESOURCE_TYPE, component.getId(), component.getOwnerId(), operation);
        return component;
    }
}
