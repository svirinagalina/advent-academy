package com.adventacademy.contentservice.domain;

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
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class LessonContentTest {

    private static final UUID LESSON_ID =
            UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

    private static final Instant FIXED_INSTANT =
            Instant.parse("2026-06-29T12:00:00Z");

    private static final Clock CLOCK =
            Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);

    // --- создание ---

    @Test
    void createShouldInitializeDraftContent() {
        LessonContent content = LessonContent.create(LESSON_ID, "# Title", CLOCK);

        assertThat(content.getId()).isNotNull();
        assertThat(content.getLessonId()).isEqualTo(LESSON_ID);
        assertThat(content.getMarkdown()).isEqualTo("# Title");
        assertThat(content.getHtml()).isNull();
        assertThat(content.getStatus()).isEqualTo(ContentStatus.DRAFT);
        assertThat(content.getCreatedAt()).isEqualTo(FIXED_INSTANT);
        assertThat(content.getUpdatedAt()).isEqualTo(FIXED_INSTANT);
    }

    @ParameterizedTest
    @MethodSource("invalidMarkdowns")
    void createShouldRejectInvalidMarkdown(String markdown) {
        assertThatThrownBy(() -> LessonContent.create(LESSON_ID, markdown, CLOCK))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("markdown must not be blank");
    }

    static Stream<Arguments> invalidMarkdowns() {
        return Stream.of(
                Arguments.of((String) null),
                Arguments.of(""),
                Arguments.of("   ")
        );
    }

    @Test
    void createShouldRejectNullLessonId() {
        assertThatThrownBy(() -> LessonContent.create(null, "markdown", CLOCK))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("lessonId must not be null");
    }

    // --- разрешённые переходы ---

    @ParameterizedTest
    @MethodSource("allowedTransitions")
    void changeStatusShouldAllowValidTransitions(ContentStatus from, ContentStatus to) {
        LessonContent content = createInStatus(from);
        Instant before = content.getUpdatedAt();

        content.changeStatus(to, CLOCK);

        assertThat(content.getStatus()).isEqualTo(to);
        assertThat(content.getUpdatedAt()).isAfterOrEqualTo(before);
    }

    static Stream<Arguments> allowedTransitions() {
        return Stream.of(
                Arguments.of(ContentStatus.DRAFT, ContentStatus.REVIEW),
                Arguments.of(ContentStatus.DRAFT, ContentStatus.ARCHIVED),
                Arguments.of(ContentStatus.REVIEW, ContentStatus.DRAFT),
                Arguments.of(ContentStatus.REVIEW, ContentStatus.PUBLISHED),
                Arguments.of(ContentStatus.REVIEW, ContentStatus.ARCHIVED),
                Arguments.of(ContentStatus.PUBLISHED, ContentStatus.ARCHIVED)
        );
    }

    // --- запрещённые переходы ---

    @ParameterizedTest
    @MethodSource("forbiddenTransitions")
    void changeStatusShouldRejectInvalidTransitions(ContentStatus from, ContentStatus to) {
        LessonContent content = createInStatus(from);

        assertThatThrownBy(() -> content.changeStatus(to, CLOCK))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(String.format("Cannot transition from %s to %s", from, to));
    }

    static Stream<Arguments> forbiddenTransitions() {
        return Stream.of(
                Arguments.of(ContentStatus.DRAFT, ContentStatus.PUBLISHED),
                Arguments.of(ContentStatus.DRAFT, ContentStatus.DRAFT),
                Arguments.of(ContentStatus.REVIEW, ContentStatus.REVIEW),
                Arguments.of(ContentStatus.PUBLISHED, ContentStatus.DRAFT),
                Arguments.of(ContentStatus.PUBLISHED, ContentStatus.REVIEW),
                Arguments.of(ContentStatus.PUBLISHED, ContentStatus.PUBLISHED),
                Arguments.of(ContentStatus.ARCHIVED, ContentStatus.DRAFT),
                Arguments.of(ContentStatus.ARCHIVED, ContentStatus.REVIEW),
                Arguments.of(ContentStatus.ARCHIVED, ContentStatus.PUBLISHED),
                Arguments.of(ContentStatus.ARCHIVED, ContentStatus.ARCHIVED)
        );
    }

    // --- гарантия, что переход «в себя» запрещён, даже по canTransitionTo ---

    @ParameterizedTest
    @EnumSource(ContentStatus.class)
    void statusShouldNotAllowTransitionToItself(ContentStatus status) {
        assertThat(status.canTransitionTo(status)).isFalse();
    }

    // --- вспомогательный фабричный метод для начального статуса ---

    private static LessonContent createInStatus(ContentStatus status) {
        LessonContent content = LessonContent.create(LESSON_ID, "markdown", CLOCK);
        if (status == ContentStatus.DRAFT) {
            return content;
        }
        content.changeStatus(ContentStatus.REVIEW, CLOCK);
        if (status == ContentStatus.REVIEW) {
            return content;
        }
        content.changeStatus(ContentStatus.PUBLISHED, CLOCK);
        if (status == ContentStatus.PUBLISHED) {
            return content;
        }
        content.changeStatus(ContentStatus.ARCHIVED, CLOCK);
        return content;
    }
}