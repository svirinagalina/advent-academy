package com.adventacademy.contentservice.infrastructure.persistence;

import com.adventacademy.contentservice.AbstractPostgresIT;
import com.adventacademy.contentservice.domain.constructor.Component;
import com.adventacademy.contentservice.domain.constructor.ComponentRef;
import com.adventacademy.contentservice.domain.constructor.Course;
import com.adventacademy.contentservice.domain.repository.ComponentRepository;
import com.adventacademy.contentservice.domain.repository.CourseRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
class CourseRepositoryAdapterIT extends AbstractPostgresIT {

    private static final Clock CLOCK = Clock.systemUTC();

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private ComponentRepository componentRepository;

    @PersistenceContext
    private EntityManager em;

    @Test
    void saveAndLoadPreservesComponentRefOrder() {
        Component c1 = componentRepository.save(Component.create("Intro", null, CLOCK));
        Component c2 = componentRepository.save(Component.create("Collections", null, CLOCK));

        Course course = Course.create("Java Basics", "About java", CLOCK);
        course.addComponent(c1.getId(), CLOCK);
        course.addComponent(c2.getId(), CLOCK);
        courseRepository.save(course);
        em.flush();
        em.clear();

        Course loaded = courseRepository.findById(course.getId()).orElseThrow();
        assertThat(loaded.getComponents())
                .extracting(ComponentRef::getComponentId)
                .containsExactly(c1.getId(), c2.getId());
        assertThat(loaded.getComponents())
                .extracting(ComponentRef::getPosition)
                .containsExactly(0, 1);
    }

    @Test
    void reorderAndSaveDoesNotViolateUniquePositionConstraint() {
        Component c1 = componentRepository.save(Component.create("Intro", null, CLOCK));
        Component c2 = componentRepository.save(Component.create("Collections", null, CLOCK));

        Course course = Course.create("Java Basics", "About java", CLOCK);
        course.addComponent(c1.getId(), CLOCK);
        course.addComponent(c2.getId(), CLOCK);
        courseRepository.save(course);
        em.flush();
        em.clear();

        Course loaded = courseRepository.findById(course.getId()).orElseThrow();
        loaded.reorderComponents(List.of(c2.getId(), c1.getId()), CLOCK);
        courseRepository.save(loaded);
        em.flush();
        em.clear();

        Course reloaded = courseRepository.findById(course.getId()).orElseThrow();
        assertThat(reloaded.getComponents())
                .extracting(ComponentRef::getComponentId)
                .containsExactly(c2.getId(), c1.getId());
    }

    @Test
    void foreignKeyPreventsDeletingComponentUsedByCourse() {
        Component component = componentRepository.save(Component.create("Intro", null, CLOCK));
        Course course = Course.create("Java Basics", "About java", CLOCK);
        course.addComponent(component.getId(), CLOCK);
        courseRepository.save(course);
        em.flush();

        assertThatThrownBy(() -> {
            componentRepository.deleteById(component.getId());
            em.flush();
        }).hasMessageContaining("constraint");
    }

    @Test
    void existsByComponentIdSeesReferencedComponents() {
        Component used = componentRepository.save(Component.create("Used", null, CLOCK));
        Component unused = componentRepository.save(Component.create("Unused", null, CLOCK));
        Course course = Course.create("Java Basics", null, CLOCK);
        course.addComponent(used.getId(), CLOCK);
        courseRepository.save(course);
        em.flush();

        assertThat(courseRepository.existsByComponentId(used.getId())).isTrue();
        assertThat(courseRepository.existsByComponentId(unused.getId())).isFalse();
    }
}
