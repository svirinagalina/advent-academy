package com.adventacademy.contentservice.domain.constructor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class LessonTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-06-29T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);

    @Test
    void createShouldInitializeLesson() {
        Lesson lesson = Lesson.create("Intro", 0, CLOCK);

        assertThat(lesson.getId()).isNotNull();
        assertThat(lesson.getTitle()).isEqualTo("Intro");
        assertThat(lesson.getPosition()).isZero();
        assertThat(lesson.getCreatedAt()).isEqualTo(FIXED_INSTANT);
        assertThat(lesson.getUpdatedAt()).isEqualTo(FIXED_INSTANT);
    }

    @ParameterizedTest
    @MethodSource("blankTitles")
    void createShouldRejectBlankTitle(String title) {
        assertThatThrownBy(() -> Lesson.create(title, 0, CLOCK))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("title must not be blank");
    }

    static Stream<Arguments> blankTitles() {
        return Stream.of(Arguments.of((String) null), Arguments.of(""), Arguments.of("   "));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, -5})
    void createShouldRejectNegativePosition(int position) {
        assertThatThrownBy(() -> Lesson.create("Intro", position, CLOCK))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("position must not be negative");
    }

    @Test
    void renameShouldUpdateTitle() {
        Lesson lesson = Lesson.create("Intro", 0, CLOCK);
        lesson.rename("Getting started", CLOCK);

        assertThat(lesson.getTitle()).isEqualTo("Getting started");
    }

    @Test
    void restoreShouldRebuildLessonAsIs() {
        UUID id = UUID.randomUUID();
        Instant created = Instant.parse("2026-01-01T00:00:00Z");
        Instant updated = Instant.parse("2026-02-01T00:00:00Z");

        Lesson lesson = Lesson.restore(id, "Restored", 3, created, updated);

        assertThat(lesson.getId()).isEqualTo(id);
        assertThat(lesson.getTitle()).isEqualTo("Restored");
        assertThat(lesson.getPosition()).isEqualTo(3);
        assertThat(lesson.getCreatedAt()).isEqualTo(created);
        assertThat(lesson.getUpdatedAt()).isEqualTo(updated);
    }
}
