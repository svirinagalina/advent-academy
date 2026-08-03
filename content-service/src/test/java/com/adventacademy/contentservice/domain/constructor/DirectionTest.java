package com.adventacademy.contentservice.domain.constructor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class DirectionTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-06-29T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);

    @Test
    void createShouldInitializeEmptyDirection() {
        Direction direction = Direction.create("Backend", "About backend", CLOCK);

        assertThat(direction.getId()).isNotNull();
        assertThat(direction.getTitle()).isEqualTo("Backend");
        assertThat(direction.getCourses()).isEmpty();
        assertThat(direction.getCreatedAt()).isEqualTo(FIXED_INSTANT);
        assertThat(direction.getUpdatedAt()).isEqualTo(FIXED_INSTANT);
    }

    @Test
    void createShouldRejectBlankTitle() {
        assertThatThrownBy(() -> Direction.create(null, null, CLOCK))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("title must not be blank");
    }

    @Test
    void addCourseShouldAppendWithContiguousPositions() {
        Direction direction = Direction.create("Backend", "About backend", CLOCK);
        UUID c1 = UUID.randomUUID();
        UUID c2 = UUID.randomUUID();

        direction.addCourse(c1, CLOCK);
        direction.addCourse(c2, CLOCK);

        assertThat(direction.getCourses())
                .extracting(CourseRef::getCourseId)
                .containsExactly(c1, c2);
        assertThat(direction.getCourses())
                .extracting(CourseRef::getPosition)
                .containsExactly(0, 1);
    }

    @Test
    void addCourseShouldRejectDuplicate() {
        Direction direction = Direction.create("Backend", "About backend", CLOCK);
        UUID c1 = UUID.randomUUID();
        direction.addCourse(c1, CLOCK);

        assertThatThrownBy(() -> direction.addCourse(c1, CLOCK))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("course already referenced");
    }

    @Test
    void getCoursesShouldReturnUnmodifiableList() {
        Direction direction = Direction.create("Backend", "About backend", CLOCK);
        direction.addCourse(UUID.randomUUID(), CLOCK);

        List<CourseRef> refs = direction.getCourses();

        assertThatThrownBy(() -> refs.add(CourseRef.create(UUID.randomUUID(), 1)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void restoreShouldOrderCoursesByPosition() {
        CourseRef second = CourseRef.restore(UUID.randomUUID(), 1);
        CourseRef first = CourseRef.restore(UUID.randomUUID(), 0);

        Direction direction = Direction.restore(UUID.randomUUID(), "Backend",
                "About backend", List.of(second, first), FIXED_INSTANT, FIXED_INSTANT, 0L);
        assertThat(direction.getCourses()).containsExactly(first, second);
    }

    @Test
    void removeCourseShouldRenumberRemaining() {
        Direction direction = Direction.create("Backend", "About backend", CLOCK);
        UUID c1 = UUID.randomUUID();
        UUID c2 = UUID.randomUUID();
        UUID c3 = UUID.randomUUID();
        direction.addCourse(c1, CLOCK);
        direction.addCourse(c2, CLOCK);
        direction.addCourse(c3, CLOCK);

        direction.removeCourse(c2, CLOCK);

        assertThat(direction.getCourses())
                .extracting(CourseRef::getCourseId)
                .containsExactly(c1, c3);
        assertThat(direction.getCourses())
                .extracting(CourseRef::getPosition)
                .containsExactly(0, 1);
    }

    @Test
    void reorderCoursesShouldApplyNewOrder() {
        Direction direction = Direction.create("Backend", "About backend", CLOCK);
        UUID c1 = UUID.randomUUID();
        UUID c2 = UUID.randomUUID();
        direction.addCourse(c1, CLOCK);
        direction.addCourse(c2, CLOCK);

        direction.reorderCourses(List.of(c2, c1), CLOCK);

        assertThat(direction.getCourses())
                .extracting(CourseRef::getCourseId)
                .containsExactly(c2, c1);
    }

    @Test
    void reorderCoursesShouldRejectForeignIds() {
        Direction direction = Direction.create("Backend", "About backend", CLOCK);
        direction.addCourse(UUID.randomUUID(), CLOCK);
        direction.addCourse(UUID.randomUUID(), CLOCK);

        assertThatThrownBy(() -> direction.reorderCourses(List.of(UUID.randomUUID(), UUID.randomUUID()), CLOCK))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("permutation");
    }

    @Test
    void reorderCoursesShouldRejectDuplicateIds() {
        Direction direction = Direction.create("Backend", "About backend", CLOCK);
        UUID c1 = UUID.randomUUID();
        UUID c2 = UUID.randomUUID();
        direction.addCourse(c1, CLOCK);
        direction.addCourse(c2, CLOCK);

        assertThatThrownBy(() -> direction.reorderCourses(List.of(c1, c1), CLOCK))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("permutation");
    }

    @Test
    void reorderCoursesShouldRejectMissingIds() {
        Direction direction = Direction.create("Backend", "About backend", CLOCK);
        UUID c1 = UUID.randomUUID();
        UUID c2 = UUID.randomUUID();
        direction.addCourse(c1, CLOCK);
        direction.addCourse(c2, CLOCK);

        assertThatThrownBy(() -> direction.reorderCourses(List.of(c1), CLOCK))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("permutation");
    }
}