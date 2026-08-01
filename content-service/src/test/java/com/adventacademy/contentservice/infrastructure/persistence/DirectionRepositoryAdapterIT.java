package com.adventacademy.contentservice.infrastructure.persistence;

import com.adventacademy.contentservice.AbstractPostgresIT;
import com.adventacademy.contentservice.domain.constructor.Course;
import com.adventacademy.contentservice.domain.constructor.CourseRef;
import com.adventacademy.contentservice.domain.constructor.Direction;
import com.adventacademy.contentservice.domain.repository.CourseRepository;
import com.adventacademy.contentservice.domain.repository.DirectionRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
class DirectionRepositoryAdapterIT extends AbstractPostgresIT {

    private static final Clock CLOCK = Clock.systemUTC();

    @Autowired
    private DirectionRepository directionRepository;

    @Autowired
    private CourseRepository courseRepository;

    @PersistenceContext
    private EntityManager em;

    @Test
    void saveAndLoadPreservesCourseRefOrder() {
        Course c1 = courseRepository.save(Course.create("Java Basics", null, CLOCK));
        Course c2 = courseRepository.save(Course.create("Java Advanced", null, CLOCK));

        Direction direction = Direction.create("Java Backend", "Path", CLOCK);
        direction.addCourse(c1.getId(), CLOCK);
        direction.addCourse(c2.getId(), CLOCK);
        directionRepository.save(direction);
        em.flush();
        em.clear();

        Direction loaded = directionRepository.findById(direction.getId()).orElseThrow();
        assertThat(loaded.getCourses())
                .extracting(CourseRef::getCourseId)
                .containsExactly(c1.getId(), c2.getId());
        assertThat(loaded.getCourses())
                .extracting(CourseRef::getPosition)
                .containsExactly(0, 1);
    }

    @Test
    void reorderAndSaveDoesNotViolateUniquePositionConstraint() {
        Course c1 = courseRepository.save(Course.create("Java Basics", null, CLOCK));
        Course c2 = courseRepository.save(Course.create("Java Advanced", null, CLOCK));

        Direction direction = Direction.create("Java Backend", "Path", CLOCK);
        direction.addCourse(c1.getId(), CLOCK);
        direction.addCourse(c2.getId(), CLOCK);
        directionRepository.save(direction);
        em.flush();
        em.clear();

        Direction loaded = directionRepository.findById(direction.getId()).orElseThrow();
        loaded.reorderCourses(List.of(c2.getId(), c1.getId()), CLOCK);
        directionRepository.save(loaded);
        em.flush();
        em.clear();

        Direction reloaded = directionRepository.findById(direction.getId()).orElseThrow();
        assertThat(reloaded.getCourses())
                .extracting(CourseRef::getCourseId)
                .containsExactly(c2.getId(), c1.getId());
    }

    @Test
    void foreignKeyPreventsDeletingCourseUsedByDirection() {
        Course course = courseRepository.save(Course.create("Java Basics", null, CLOCK));
        Direction direction = Direction.create("Java Backend", "Path", CLOCK);
        direction.addCourse(course.getId(), CLOCK);
        directionRepository.save(direction);
        em.flush();

        assertThatThrownBy(() -> {
            courseRepository.deleteById(course.getId());
            em.flush();
        }).hasMessageContaining("constraint");
    }

    @Test
    void existsByCourseIdSeesReferencedCourses() {
        Course used = courseRepository.save(Course.create("Used", null, CLOCK));
        Course unused = courseRepository.save(Course.create("Unused", null, CLOCK));
        Direction direction = Direction.create("Java Backend", null, CLOCK);
        direction.addCourse(used.getId(), CLOCK);
        directionRepository.save(direction);
        em.flush();

        assertThat(directionRepository.existsByCourseId(used.getId())).isTrue();
        assertThat(directionRepository.existsByCourseId(unused.getId())).isFalse();
    }

    @Test
    void deletingDirectionCascadesToCourseRefsButKeepsCourses() {
        Course course = courseRepository.save(Course.create("Java Basics", null, CLOCK));
        Direction direction = Direction.create("Java Backend", null, CLOCK);
        direction.addCourse(course.getId(), CLOCK);
        directionRepository.save(direction);
        em.flush();
        em.clear();

        directionRepository.deleteById(direction.getId());
        em.flush();

        assertThat(directionRepository.findById(direction.getId())).isEmpty();
        assertThat(courseRepository.findById(course.getId())).isPresent();
    }
}