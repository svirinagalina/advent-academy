package com.adventacademy.contentservice.infrastructure.persistence;

import com.adventacademy.contentservice.AbstractPostgresIT;
import com.adventacademy.contentservice.domain.constructor.Component;
import com.adventacademy.contentservice.domain.constructor.Lesson;
import com.adventacademy.contentservice.domain.repository.ComponentRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class ComponentRepositoryAdapterIT extends AbstractPostgresIT {

    private static final Clock CLOCK = Clock.systemUTC();
    private static final UUID OWNER = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired
    private ComponentRepository repository;

    @PersistenceContext
    private EntityManager em;

    @Test
    void saveAndLoadPreservesLessonOrder() {
        Component component = Component.create("Collections", "About collections", OWNER, CLOCK);
        Lesson first = component.addLesson("Lists", CLOCK);
        Lesson second = component.addLesson("Maps", CLOCK);
        Lesson third = component.addLesson("Sets", CLOCK);
        repository.save(component);
        em.flush();
        em.clear();

        Component loaded = repository.findById(component.getId()).orElseThrow();

        assertThat(loaded.getLessons())
                .extracting(Lesson::getId)
                .containsExactly(first.getId(), second.getId(), third.getId());
        assertThat(loaded.getLessons())
                .extracting(Lesson::getPosition)
                .containsExactly(0, 1, 2);
    }

    @Test
    void reorderAndSaveDoesNotViolateUniquePositionConstraint() {
        Component component = Component.create("Collections", "About collections", OWNER, CLOCK);
        Lesson first = component.addLesson("Lists", CLOCK);
        Lesson second = component.addLesson("Maps", CLOCK);
        repository.save(component);
        em.flush();
        em.clear();

        // swap positions: without DEFERRABLE INITIALLY DEFERRED the row-by-row
        // UPDATE would hit the unique (component_id, position) constraint
        Component loaded = repository.findById(component.getId()).orElseThrow();
        loaded.reorderLessons(List.of(second.getId(), first.getId()), CLOCK);
        repository.save(loaded);
        em.flush();
        em.clear();

        Component reloaded = repository.findById(component.getId()).orElseThrow();
        assertThat(reloaded.getLessons())
                .extracting(Lesson::getId)
                .containsExactly(second.getId(), first.getId());
    }

    @Test
    void removedLessonIsDeletedFromDatabase() {
        Component component = Component.create("Collections", "About collections", OWNER, CLOCK);
        Lesson first = component.addLesson("Lists", CLOCK);
        Lesson second = component.addLesson("Maps", CLOCK);
        repository.save(component);
        em.flush();
        em.clear();

        Component loaded = repository.findById(component.getId()).orElseThrow();
        loaded.removeLesson(first.getId(), CLOCK);
        repository.save(loaded);
        em.flush();
        em.clear();

        Long rows = em.createQuery("select count(l) from LessonEntity l where l.component.id = :id", Long.class)
                .setParameter("id", component.getId())
                .getSingleResult();
        assertThat(rows).isEqualTo(1);

        Component reloaded = repository.findById(component.getId()).orElseThrow();
        assertThat(reloaded.getLessons())
                .extracting(Lesson::getId)
                .containsExactly(second.getId());
        assertThat(reloaded.getLessons().get(0).getPosition()).isZero();
    }

    @Test
    void deletingComponentCascadesToLessons() {
        Component component = Component.create("Collections", "About collections", OWNER, CLOCK);
        component.addLesson("Lists", CLOCK);
        repository.save(component);
        em.flush();
        em.clear();

        repository.deleteById(component.getId());
        em.flush();

        UUID id = component.getId();
        Long rows = em.createQuery("select count(l) from LessonEntity l where l.component.id = :id", Long.class)
                .setParameter("id", id)
                .getSingleResult();
        assertThat(rows).isZero();
        assertThat(repository.findById(id)).isEmpty();
    }
}
