package com.adventacademy.contentservice.domain.constructor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ComponentTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-06-29T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
    private static final ComponentPublicationPolicy ALL_READY = lessonIds -> Set.of();

    @Test
    void createShouldInitializeEmptyDraftComponent() {
        Component component = Component.create("Basics", "About basics", CLOCK);

        assertThat(component.getId()).isNotNull();
        assertThat(component.getTitle()).isEqualTo("Basics");
        assertThat(component.getStatus()).isEqualTo(PublicationStatus.DRAFT);
        assertThat(component.getLessons()).isEmpty();
        assertThat(component.getCreatedAt()).isEqualTo(FIXED_INSTANT);
        assertThat(component.getUpdatedAt()).isEqualTo(FIXED_INSTANT);
    }

    @Test
    void createShouldRejectBlankTitle() {
        assertThatThrownBy(() -> Component.create("  ", null, CLOCK))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("title must not be blank");
    }

    @Test
    void addLessonShouldAppendWithContiguousPositions() {
        Component component = Component.create("Basics", "About basics", CLOCK);

        Lesson first = component.addLesson("Lesson 1", CLOCK);
        Lesson second = component.addLesson("Lesson 2", CLOCK);
        Lesson third = component.addLesson("Lesson 3", CLOCK);

        assertThat(component.getLessons())
                .containsExactly(first, second, third)
                .extracting(Lesson::getPosition)
                .containsExactly(0, 1, 2);
    }

    @Test
    void getLessonsShouldReturnUnmodifiableList() {
        Component component = Component.create("Basics", "About basics", CLOCK);
        component.addLesson("Lesson 1", CLOCK);

        List<Lesson> lessons = component.getLessons();

        assertThatThrownBy(() -> lessons.add(Lesson.create("X", 1, CLOCK)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void publishAndArchiveShouldFollowLifecycle() {
        Component component = Component.create("Basics", "About basics", CLOCK);
        component.addLesson("Lesson 1", CLOCK);

        component.publish(ALL_READY, CLOCK);
        assertThat(component.getStatus()).isEqualTo(PublicationStatus.PUBLISHED);

        component.archive(CLOCK);
        assertThat(component.getStatus()).isEqualTo(PublicationStatus.ARCHIVED);
    }

    @Test
    void publishShouldRejectArchivedComponent() {
        Component component = Component.create("Basics", "About basics", CLOCK);
        component.addLesson("Lesson 1", CLOCK);
        component.archive(CLOCK);

        assertThatThrownBy(() -> component.publish(ALL_READY, CLOCK))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Cannot transition from ARCHIVED to PUBLISHED");
    }

    @Test
    void restoreShouldOrderLessonsByPosition() {
        UUID id = UUID.randomUUID();
        Lesson second = Lesson.restore(UUID.randomUUID(), "Second", 1, FIXED_INSTANT, FIXED_INSTANT);
        Lesson first = Lesson.restore(UUID.randomUUID(), "First", 0, FIXED_INSTANT, FIXED_INSTANT);

        Component component = Component.restore(id, "Basics", "About basics", PublicationStatus.PUBLISHED,
                List.of(second, first), FIXED_INSTANT, FIXED_INSTANT, 0L);

        assertThat(component.getId()).isEqualTo(id);
        assertThat(component.getStatus()).isEqualTo(PublicationStatus.PUBLISHED);
        assertThat(component.getLessons()).containsExactly(first, second);
    }

    @Test
    void removeLessonShouldRenumberRemaining() {
        Component component = Component.create("Basics", "About basics", CLOCK);
        Lesson first = component.addLesson("Lesson 1", CLOCK);
        Lesson second = component.addLesson("Lesson 2", CLOCK);
        Lesson third = component.addLesson("Lesson 3", CLOCK);

        component.removeLesson(second.getId(), CLOCK);

        assertThat(component.getLessons())
                .containsExactly(first, third)
                .extracting(Lesson::getPosition)
                .containsExactly(0, 1);
    }

    @Test
    void reorderLessonsShouldApplyNewOrder() {
        Component component = Component.create("Basics", "About basics", CLOCK);
        Lesson first = component.addLesson("Lesson 1", CLOCK);
        Lesson second = component.addLesson("Lesson 2", CLOCK);
        Lesson third = component.addLesson("Lesson 3", CLOCK);

        component.reorderLessons(List.of(third.getId(), first.getId(), second.getId()), CLOCK);

        assertThat(component.getLessons())
                .containsExactly(third, first, second)
                .extracting(Lesson::getPosition)
                .containsExactly(0, 1, 2);
    }

    @Test
    void reorderLessonsShouldRejectForeignOrMissingIds() {
        Component component = Component.create("Basics", "About basics", CLOCK);
        component.addLesson("Lesson 1", CLOCK);
        component.addLesson("Lesson 2", CLOCK);

        assertThatThrownBy(() -> component.reorderLessons(List.of(UUID.randomUUID(), UUID.randomUUID()), CLOCK))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void modificationShouldBeRejectedWhenNotDraft() {
        Component component = Component.create("Basics", "About basics", CLOCK);
        Lesson lesson = component.addLesson("Lesson 1", CLOCK);
        component.publish(ALL_READY, CLOCK);

        assertThatThrownBy(() -> component.addLesson("Lesson 2", CLOCK))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> component.removeLesson(lesson.getId(), CLOCK))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> component.rename("New", "New", CLOCK))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void restoreShouldTolerateNullLessons() {
        Component component = Component.restore(UUID.randomUUID(), "Basics", "About basics",
                PublicationStatus.DRAFT, null, FIXED_INSTANT, FIXED_INSTANT, 0L);

        assertThat(component.getLessons()).isEmpty();
    }

    @Test
    void publishShouldRejectComponentWithoutLessons() {
        Component component = Component.create("Basics", "About basics", CLOCK);

        assertThatThrownBy(() -> component.publish(ALL_READY, CLOCK))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Component must have at least one lesson to be published");
    }

    @Test
    void publishShouldReportUnpublishableLessons() {
        Component component = Component.create("Basics", "About basics", CLOCK);
        Lesson lesson = component.addLesson("Lesson 1", CLOCK);
        ComponentPublicationPolicy notReady = lessonIds -> Set.of(lesson.getId());

        assertThatThrownBy(() -> component.publish(notReady, CLOCK))
                .isInstanceOf(ComponentNotReadyForPublicationException.class)
                .satisfies(e -> assertThat(((ComponentNotReadyForPublicationException) e).getLessonIds())
                        .containsExactly(lesson.getId()));

        assertThat(component.getStatus()).isEqualTo(PublicationStatus.DRAFT);
    }
}
