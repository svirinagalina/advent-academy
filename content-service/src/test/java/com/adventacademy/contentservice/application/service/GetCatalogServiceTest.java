package com.adventacademy.contentservice.application.service;

import com.adventacademy.contentservice.domain.constructor.Course;
import com.adventacademy.contentservice.domain.constructor.Direction;
import com.adventacademy.contentservice.domain.repository.ComponentRepository;
import com.adventacademy.contentservice.domain.repository.CourseRepository;
import com.adventacademy.contentservice.domain.repository.DirectionRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

public class GetCatalogServiceTest {
    private final CourseRepository courseRepository = mock(CourseRepository.class);
    private final ComponentRepository componentRepository = mock(ComponentRepository.class);
    private final DirectionRepository directionRepository = mock(DirectionRepository.class);

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-17T12:00:00Z"), ZoneOffset.UTC);

    private final GetCatalogService service =
            new GetCatalogService(courseRepository, componentRepository, directionRepository);
    @Test
    void getCatalogShouldReturnOnlyPublishedCourses() {
        Course published = Course.create("Java course", "About java", CLOCK);
        published.addComponent(UUID.randomUUID(), CLOCK);
        published.publish(ids -> Set.of(), CLOCK);

        Course draft = Course.create("Python course", "About python", CLOCK);

        Direction direction = Direction.create("Backend", "About backend", CLOCK);
        direction.addCourse(published.getId(), CLOCK);
        direction.addCourse(draft.getId(), CLOCK);

        when(directionRepository.findAll()).thenReturn(List.of(direction));
        when(courseRepository.findAllById(List.of(published.getId(), draft.getId())))
                .thenReturn(List.of(published, draft));
        GetCatalogService.Catalog catalog = service.getCatalog();

        assertThat(catalog.directions()).hasSize(1);
        assertThat(catalog.coursesById()).containsOnlyKeys(published.getId());
    }
}
