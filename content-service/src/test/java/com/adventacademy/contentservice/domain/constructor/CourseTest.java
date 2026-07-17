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

class CourseTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-06-29T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
    private static final CoursePublicationPolicy ALL_PUBLISHED = componentIds -> Set.of();

    @Test
    void createShouldInitializeEmptyDraftCourse() {
        Course course = Course.create("Java", "About java", CLOCK);

        assertThat(course.getId()).isNotNull();
        assertThat(course.getTitle()).isEqualTo("Java");
        assertThat(course.getStatus()).isEqualTo(PublicationStatus.DRAFT);
        assertThat(course.getComponents()).isEmpty();
        assertThat(course.getCreatedAt()).isEqualTo(FIXED_INSTANT);
        assertThat(course.getUpdatedAt()).isEqualTo(FIXED_INSTANT);
    }

    @Test
    void createShouldRejectBlankTitle() {
        assertThatThrownBy(() -> Course.create("", null, CLOCK))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("title must not be blank");
    }

    @Test
    void addComponentShouldAppendWithContiguousPositions() {
        Course course = Course.create("Java", "About java", CLOCK);
        UUID c1 = UUID.randomUUID();
        UUID c2 = UUID.randomUUID();

        course.addComponent(c1, CLOCK);
        course.addComponent(c2, CLOCK);

        assertThat(course.getComponents())
                .extracting(ComponentRef::getComponentId)
                .containsExactly(c1, c2);
        assertThat(course.getComponents())
                .extracting(ComponentRef::getPosition)
                .containsExactly(0, 1);
    }

    @Test
    void addComponentShouldRejectDuplicate() {
        Course course = Course.create("Java", "About java", CLOCK);
        UUID c1 = UUID.randomUUID();
        course.addComponent(c1, CLOCK);

        assertThatThrownBy(() -> course.addComponent(c1, CLOCK))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("component already referenced");
    }

    @Test
    void addComponentShouldRejectNull() {
        Course course = Course.create("Java", "About java", CLOCK);

        assertThatThrownBy(() -> course.addComponent(null, CLOCK))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("componentId must not be null");
    }

    @Test
    void getComponentsShouldReturnUnmodifiableList() {
        Course course = Course.create("Java", "About java", CLOCK);
        course.addComponent(UUID.randomUUID(), CLOCK);

        List<ComponentRef> refs = course.getComponents();

        assertThatThrownBy(() -> refs.add(ComponentRef.create(UUID.randomUUID(), 1)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void restoreShouldOrderComponentsByPosition() {
        ComponentRef second = ComponentRef.restore(UUID.randomUUID(), 1);
        ComponentRef first = ComponentRef.restore(UUID.randomUUID(), 0);

        Course course = Course.restore(UUID.randomUUID(), "Java", "About Java", PublicationStatus.PUBLISHED,
                List.of(second, first), FIXED_INSTANT, FIXED_INSTANT, 0L);

        assertThat(course.getComponents()).containsExactly(first, second);
    }

    @Test
    void removeComponentShouldRenumberRemaining() {
        Course course = Course.create("Java", "About java", CLOCK);
        UUID c1 = UUID.randomUUID();
        UUID c2 = UUID.randomUUID();
        UUID c3 = UUID.randomUUID();
        course.addComponent(c1, CLOCK);
        course.addComponent(c2, CLOCK);
        course.addComponent(c3, CLOCK);

        course.removeComponent(c2, CLOCK);

        assertThat(course.getComponents())
                .extracting(ComponentRef::getComponentId)
                .containsExactly(c1, c3);
        assertThat(course.getComponents())
                .extracting(ComponentRef::getPosition)
                .containsExactly(0, 1);
    }

    @Test
    void reorderComponentsShouldApplyNewOrder() {
        Course course = Course.create("Java", "About java", CLOCK);
        UUID c1 = UUID.randomUUID();
        UUID c2 = UUID.randomUUID();
        course.addComponent(c1, CLOCK);
        course.addComponent(c2, CLOCK);

        course.reorderComponents(List.of(c2, c1), CLOCK);

        assertThat(course.getComponents())
                .extracting(ComponentRef::getComponentId)
                .containsExactly(c2, c1);
    }

    @Test
    void publishShouldReportUnpublishedComponents() {
        Course course = Course.create("Java", "About java", CLOCK);
        UUID componentId = UUID.randomUUID();
        course.addComponent(componentId, CLOCK);
        CoursePublicationPolicy notReady = componentIds -> Set.of(componentId);

        assertThatThrownBy(() -> course.publish(notReady, CLOCK))
                .isInstanceOf(CourseNotReadyForPublicationException.class)
                .satisfies(e -> assertThat(((CourseNotReadyForPublicationException) e).getComponentIds())
                        .containsExactly(componentId));
        assertThat(course.getStatus()).isEqualTo(PublicationStatus.DRAFT);
    }

    @Test
    void modificationShouldBeRejectedWhenNotDraft() {
        Course course = Course.create("Java", "About java", CLOCK);
        UUID componentId = UUID.randomUUID();
        course.addComponent(componentId, CLOCK);
        course.publish(ALL_PUBLISHED, CLOCK);

        assertThatThrownBy(() -> course.addComponent(UUID.randomUUID(), CLOCK))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> course.removeComponent(componentId, CLOCK))
                .isInstanceOf(IllegalStateException.class);
    }
}
