package com.adventacademy.contentservice.domain.constructor;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class PublicationStatusTest {

    @ParameterizedTest
    @MethodSource("allowedTransitions")
    void shouldAllowValidTransitions(PublicationStatus from, PublicationStatus to) {
        assertThat(from.canTransitionTo(to)).isTrue();
    }

    static Stream<Arguments> allowedTransitions() {
        return Stream.of(
                Arguments.of(PublicationStatus.DRAFT, PublicationStatus.PUBLISHED),
                Arguments.of(PublicationStatus.DRAFT, PublicationStatus.ARCHIVED),
                Arguments.of(PublicationStatus.PUBLISHED, PublicationStatus.ARCHIVED)
        );
    }

    @ParameterizedTest
    @MethodSource("forbiddenTransitions")
    void shouldRejectInvalidTransitions(PublicationStatus from, PublicationStatus to) {
        assertThat(from.canTransitionTo(to)).isFalse();
    }

    static Stream<Arguments> forbiddenTransitions() {
        return Stream.of(
                Arguments.of(PublicationStatus.ARCHIVED, PublicationStatus.DRAFT),
                Arguments.of(PublicationStatus.ARCHIVED, PublicationStatus.PUBLISHED),
                Arguments.of(PublicationStatus.PUBLISHED, PublicationStatus.DRAFT)
                );
    }

    @ParameterizedTest
    @EnumSource(PublicationStatus.class)
    void shouldNotAllowTransitionToItself(PublicationStatus status) {
        assertThat(status.canTransitionTo(status)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(PublicationStatus.class)
    void shouldNotAllowTransitionToNull(PublicationStatus status) {
        assertThat(status.canTransitionTo(null)).isFalse();
    }
}
