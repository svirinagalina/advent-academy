package com.adventacademy.contentservice.application.service;

import com.adventacademy.contentservice.domain.constructor.Component;
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
 */
@Service
@Transactional
public class ComponentStructureService {

    private final ComponentRepository componentRepository;
    private final CourseRepository courseRepository;
    private final Clock clock;

    public ComponentStructureService(ComponentRepository componentRepository,
                                     CourseRepository courseRepository) {
        this.componentRepository = componentRepository;
        this.courseRepository = courseRepository;
        this.clock = Clock.systemUTC();
    }

    public Component create(String title, String description) {
        Component component = Component.create(title, description, clock);
        return componentRepository.save(component);
    }

    @Transactional(readOnly = true)
    public List<Component> findAll() {
        return componentRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Component get(UUID id) {
        return componentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Component not found: " + id));
    }

    public Component rename(UUID id, String title, String description) {
        Component component = get(id);
        component.rename(title, description, clock);
        return componentRepository.save(component);
    }

    public void delete(UUID id) {
        Component component = get(id);
        if (courseRepository.existsByComponentId(id)) {
            throw new ComponentInUseException(id);
        }
        component.ensureDeletable();
        componentRepository.deleteById(id);
    }

    public Component addLesson(UUID componentId, String lessonTitle) {
        Component component = get(componentId);
        component.addLesson(lessonTitle, clock);
        return componentRepository.save(component);
    }

    public Component renameLesson(UUID componentId, UUID lessonId, String title) {
        Component component = get(componentId);
        component.renameLesson(lessonId, title, clock);
        return componentRepository.save(component);
    }

    public Component removeLesson(UUID componentId, UUID lessonId) {
        Component component = get(componentId);
        component.removeLesson(lessonId, clock);
        return componentRepository.save(component);
    }

    public Component reorderLessons(UUID componentId, List<UUID> orderedIds) {
        Component component = get(componentId);
        component.reorderLessons(orderedIds, clock);
        return componentRepository.save(component);
    }
}