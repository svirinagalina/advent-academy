package com.adventacademy.contentservice.application.service;

import com.adventacademy.contentservice.domain.constructor.Component;
import com.adventacademy.contentservice.domain.constructor.Direction;
import com.adventacademy.contentservice.domain.constructor.PublicationStatus;
import com.adventacademy.contentservice.domain.repository.CourseRepository;
import com.adventacademy.contentservice.domain.repository.DirectionRepository;
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

public class DirectionAssemblyServiceTest {
    private final DirectionRepository directionRepository = mock(DirectionRepository.class);
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

    private final DirectionAssemblyService service =
            new DirectionAssemblyService(directionRepository, courseRepository, accessGuard);

    @Test
    void addCourseShouldThrowWhenCourseDoesNotExist() {
        UUID courseId = UUID.randomUUID();
        UUID directionId = UUID.randomUUID();
        when(courseRepository.existsById(courseId)).thenReturn(false);

        assertThatThrownBy(() -> service.addCourse(directionId, courseId))
                .isInstanceOf(IllegalArgumentException.class);

        verify(directionRepository, never()).save(any());
    }

    @Test
    void addCourseShouldAppendCourseToDirection() {
        UUID courseId = UUID.randomUUID();
        Direction direction = Direction.create("Backend", "About backend", OWNER, CLOCK);
        when(courseRepository.existsById(courseId)).thenReturn(true);
        when(directionRepository.findById(direction.getId())).thenReturn(Optional.of(direction));
        when(directionRepository.save(any(Direction.class))).thenAnswer(inv -> inv.getArgument(0));

        Direction result = service.addCourse(direction.getId(), courseId);

        assertThat(result.getCourses()).hasSize(1);
        assertThat(result.getCourses().get(0).getCourseId()).isEqualTo(courseId);
        verify(directionRepository).save(direction);
    }

    @Test
    void createShouldSaveDirection() {
        when(directionRepository.save(any(Direction.class))).thenAnswer(inv -> inv.getArgument(0));

        Direction result = service.create("Backend", "About backend");

        assertThat(result.getTitle()).isEqualTo("Backend");
        verify(directionRepository).save(result);
    }
}
