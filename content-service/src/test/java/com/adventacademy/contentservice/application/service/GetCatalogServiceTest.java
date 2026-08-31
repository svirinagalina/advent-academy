package com.adventacademy.contentservice.adapter.rest.dto;

import com.adventacademy.contentservice.domain.constructor.Course;
import com.adventacademy.contentservice.domain.constructor.Direction;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.UUID;

class CatalogResponseTest {

    private static final UUID OWNER_ID = UUID.randomUUID();


    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-17T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void directionWithoutPublishedCoursesIsHiddenFromCatalog() {
        Course draft = Course.create("Python course", "About python", OWNER_ID, CLOCK);
        Direction direction = Direction.create("Backend", "About backend", CLOCK);
        direction.addCourse(draft.getId(), CLOCK);

        // сервис уже отфильтровал draft-курсы, поэтому карта пустая
        CatalogResponse response = CatalogResponse.from(List.of(direction), Map.of());

        assertThat(response.directions()).isEmpty();
    }

    @Test
    void directionWithPublishedCourseStaysInCatalog() {
        Course published = Course.create("Java course", "About java", OWNER_ID, CLOCK);
        Direction direction = Direction.create("Backend", "About backend", CLOCK);
        direction.addCourse(published.getId(), CLOCK);

        CatalogResponse response = CatalogResponse.from(
                List.of(direction), Map.of(published.getId(), published));

        assertThat(response.directions()).hasSize(1);
        assertThat(response.directions().get(0).courses()).hasSize(1);
    }
}