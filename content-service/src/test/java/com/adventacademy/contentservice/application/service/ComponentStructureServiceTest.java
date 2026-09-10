package com.adventacademy.contentservice.application.service;

import com.adventacademy.contentservice.domain.constructor.Course;
import com.adventacademy.contentservice.domain.constructor.PublicationStatus;
import com.adventacademy.contentservice.domain.repository.ComponentRepository;
import com.adventacademy.contentservice.domain.repository.CourseRepository;
import com.adventacademy.contentservice.domain.constructor.Component;
import com.adventacademy.contentservice.application.port.AuditTrailPort;
import com.adventacademy.contentservice.application.port.CurrentUserProvider;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ComponentStructureServiceTest {

    private final ComponentRepository componentRepository = mock(ComponentRepository.class);
    private final CourseRepository courseRepository = mock(CourseRepository.class);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-17T12:00:00Z"), ZoneOffset.UTC);
    private static final UUID OWNER = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private static final UUID OTHER_USER = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private final AuditTrailPort auditTrail = mock(AuditTrailPort.class);

    /** Hand-written stand-in: the role logic under test lives on the port's default methods. */
    private static final class FakeCurrentUser implements CurrentUserProvider {

        private UUID userId = OWNER;
        private Set<String> roles = Set.of("INSTRUCTOR");

        @Override
        public Optional<UUID> currentUserId() {
            return Optional.ofNullable(userId);
        }

        @Override
        public Set<String> currentRoles() {
            return roles;
        }
    }

    private final FakeCurrentUser currentUser = new FakeCurrentUser();
    private final ContentAccessGuard accessGuard = new ContentAccessGuard(currentUser, auditTrail);

    private final ComponentStructureService service = new ComponentStructureService(componentRepository, courseRepository, accessGuard);

    @Test
    void deleteShouldThrowWhenComponentIsUsedByCourse() {
        Component component = Component.create("Basics", "About basics", OWNER, CLOCK);
        when(componentRepository.findById(component.getId())).thenReturn(Optional.of(component));
        when(courseRepository.existsByComponentId(component.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.delete(component.getId())).isInstanceOf(ComponentInUseException.class);
        verify(componentRepository, never()).deleteById(any());
    }

    @Test
    void deleteShouldDeleteWhenComponentIsNotUsed() {
        Component component = Component.create("Basics", "About basics", OWNER, CLOCK);
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

    @Test
    void createShouldSaveDraftComponent() {
        when(componentRepository.save(any(Component.class))).thenAnswer(inv -> inv.getArgument(0));

        Component result = service.create("Basics", "About basics");

        assertThat(result.getTitle()).isEqualTo("Basics");
        assertThat(result.getStatus()).isEqualTo(PublicationStatus.DRAFT);
        verify(componentRepository).save(result);
    }

    @Test
    void renameShouldUpdateTitle() {
        Component component = Component.create("Basics", "About basics", OWNER, CLOCK);
        when(componentRepository.findById(component.getId())).thenReturn(Optional.of(component));
        when(componentRepository.save(any(Component.class))).thenAnswer(inv -> inv.getArgument(0));

        Component result = service.rename(component.getId(), "New title", "New description");

        assertThat(result.getTitle()).isEqualTo("New title");
        verify(componentRepository).save(component);
    }

    @Test
    void addLessonShouldAppendLessonToComponent() {
        Component component = Component.create("Basics", "About basics", OWNER, CLOCK);
        when(componentRepository.findById(component.getId())).thenReturn(Optional.of(component));
        when(componentRepository.save(any(Component.class))).thenAnswer(inv -> inv.getArgument(0));
        Component result = service.addLesson(component.getId(), "Lesson");
        assertThat(result.getLessons()).hasSize(1);
        assertThat(result.getLessons().get(0).getTitle()).isEqualTo("Lesson");
        verify(componentRepository).save(component);

    }
}