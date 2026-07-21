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
 */
@Service
@Transactional
public class ComponentLifecycleService {

    private final ComponentRepository componentRepository;
    private final CourseRepository courseRepository;
    private final LessonContentRepository lessonContentRepository;
    private final ComponentPublicationPolicy publicationPolicy;
    private final Clock clock;

    public ComponentLifecycleService(ComponentRepository componentRepository,
                                     CourseRepository courseRepository,
                                     LessonContentRepository lessonContentRepository,
                                     ComponentPublicationPolicy publicationPolicy) {
        this.componentRepository = componentRepository;
        this.courseRepository = courseRepository;
        this.lessonContentRepository = lessonContentRepository;
        this.publicationPolicy = publicationPolicy;
        this.clock = Clock.systemUTC();
    }

    public Component publish(UUID id) {
        Component component = get(id);
        component.publish(publicationPolicy, clock);
        return componentRepository.save(component);
    }

    public Component archive(UUID id) {
        Component component = get(id);
        component.archive(clock);
        return componentRepository.save(component);
    }

    /**
     * Deep copy of a component: new component and lesson ids, DRAFT status.
     * Lesson content of the original lessons is copied as DRAFT for the new lessons.
     */
    public Component clone(UUID id) {
        Component original = get(id);
        Component copy = Component.create(original.getTitle(), original.getDescription(), clock);
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
        Component component = get(id);
        Course course = Course.create(component.getTitle(), component.getDescription(), clock);
        course.addComponent(component.getId(), clock);
        return courseRepository.save(course);
    }

    private Component get(UUID id) {
        return componentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Component not found: " + id));
    }
}
