package com.adventacademy.contentservice.application.service;

import com.adventacademy.contentservice.domain.repository.ComponentRepository;
import com.adventacademy.contentservice.domain.repository.CourseRepository;
import com.adventacademy.contentservice.domain.constructor.Component;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ComponentStructureServiceTest {

    private final ComponentRepository componentRepository = mock(ComponentRepository.class);
    private final CourseRepository courseRepository = mock(CourseRepository.class);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-17T12:00:00Z"), ZoneOffset.UTC);

    private final ComponentStructureService service = new ComponentStructureService(componentRepository, courseRepository);

    @Test
    void deleteShouldThrowWhenComponentIsUsedByCourse() {
        Component component = Component.create("Basics", "About basics", CLOCK);
        when(componentRepository.findById(component.getId())).thenReturn(Optional.of(component));
        when(courseRepository.existsByComponentId(component.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.delete(component.getId())).isInstanceOf(ComponentInUseException.class);
        verify(componentRepository, never()).deleteById(any());
    }

    @Test
    void deleteShouldDeleteWhenComponentIsNotUsed() {
        Component component = Component.create("Basics", "About basics", CLOCK);
        when(componentRepository.findById(component.getId())).thenReturn(Optional.of(component));
        when(courseRepository.existsByComponentId(component.getId())).thenReturn(false);

        service.delete(component.getId());

        verify(componentRepository).deleteById(component.getId());
    }

    @Test
    void deleteShouldThrowWhenComponentDoesNotExist() {
        UUID id = UUID.randomUUID();
        when(componentRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(id))
                .isInstanceOf(NotFoundException.class);

        verify(componentRepository, never()).deleteById(any());
    }
}