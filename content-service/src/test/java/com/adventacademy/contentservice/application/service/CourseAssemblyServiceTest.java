package com.adventacademy.contentservice.application.service;

import com.adventacademy.contentservice.domain.constructor.*;
import com.adventacademy.contentservice.domain.repository.ComponentRepository;
import com.adventacademy.contentservice.domain.repository.CourseRepository;
import com.adventacademy.contentservice.domain.repository.DirectionRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import com.adventacademy.contentservice.application.port.AuditTrailPort;
import com.adventacademy.contentservice.application.port.CurrentUserProvider;

public class CourseAssemblyServiceTest {

    private static final UUID OWNER_ID = UUID.randomUUID();
    private static final UUID OTHER_USER_ID = UUID.randomUUID();

    private final ComponentRepository componentRepository = mock(ComponentRepository.class);
    private final CourseRepository courseRepository = mock(CourseRepository.class);
    private final DirectionRepository directionRepository = mock(DirectionRepository.class);
    private final CoursePublicationPolicy coursePublicationPolicy = mock(CoursePublicationPolicy.class);
    private final AuditTrailPort auditTrail = mock(AuditTrailPort.class);

    /**
     * Hand-written stand-in: the default {@code hasAnyRole} logic under test lives on the port
     * itself, so a mock would have to be told to call it back.
     */
    private static final class FakeCurrentUser implements CurrentUserProvider {

        private UUID userId = OWNER_ID;
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

    private final FakeCurrentUser currentUserProvider = new FakeCurrentUser();

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-17T12:00:00Z"), ZoneOffset.UTC);
    private final CourseAssemblyService service = new CourseAssemblyService(
            courseRepository, componentRepository, directionRepository, coursePublicationPolicy,
            currentUserProvider, auditTrail, mock(org.springframework.jdbc.core.JdbcTemplate.class));

    @Test
    void addComponentShouldThrowWhenComponentDoesNotExist() {
        UUID courseId = UUID.randomUUID();
        UUID componentId = UUID.randomUUID();
        when(componentRepository.findById(componentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addComponent(courseId, componentId))
                .isInstanceOf(IllegalArgumentException.class);

        verify(courseRepository, never()).save(any());
    }

    @Test
    void deleteShouldThrowWhenCourseIsUsedByDirection() {
        Course course = Course.create("Java course", "About java", OWNER_ID, CLOCK);
        when(courseRepository.findById(course.getId())).thenReturn(Optional.of(course));
        when(directionRepository.existsByCourseId(course.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.delete(course.getId()))
                .isInstanceOf(CourseInUseException.class);

        verify(courseRepository, never()).deleteById(any());
    }

    @Test
    void deleteShouldDeleteWhenCoursetIsNotUsed() {
        Course course = Course.create("Basics", "About basics", OWNER_ID, CLOCK);
        when(courseRepository.findById(course.getId())).thenReturn(Optional.of(course));
        when(directionRepository.existsByCourseId(course.getId())).thenReturn(false);

        service.delete(course.getId());

        verify(courseRepository).deleteById(course.getId());
    }

    @Test
    void publishShouldThrowWhenComponentsAreNotPublished() {
        UUID componentId = UUID.randomUUID();
        Course course = Course.create("Java course", "About java", OWNER_ID, CLOCK);
        course.addComponent(componentId, CLOCK);
        when(courseRepository.findById(course.getId())).thenReturn(Optional.of(course));
        when(coursePublicationPolicy.findUnpublishedComponents(List.of(componentId)))
                .thenReturn(Set.of(componentId));

        assertThatThrownBy(() -> service.publish(course.getId()))
                .isInstanceOf(CourseNotReadyForPublicationException.class);

        verify(courseRepository, never()).save(any());
    }

    @Test
    void publishShouldPublishWhenAllComponentsArePublished() {
        UUID componentId = UUID.randomUUID();
        Course course = Course.create("Java course", "About java", OWNER_ID, CLOCK);
        course.addComponent(componentId, CLOCK);
        when(courseRepository.findById(course.getId())).thenReturn(Optional.of(course));
        when(coursePublicationPolicy.findUnpublishedComponents(List.of(componentId))).thenReturn(Set.of());
        when(courseRepository.save(any(Course.class))).thenAnswer(inv -> inv.getArgument(0));

        Course result = service.publish(course.getId());

        assertThat(result.getStatus()).isEqualTo(PublicationStatus.PUBLISHED);
        verify(courseRepository).save(course);
    }

    @Test
    void renameShouldBeRefusedForACourseOfAnotherUser() {
        Course course = Course.create("Foreign course", "Not mine", OTHER_USER_ID, CLOCK);
        when(courseRepository.findById(course.getId())).thenReturn(Optional.of(course));

        assertThatThrownBy(() -> service.rename(course.getId(), "Hijacked", "Nope"))
                .isInstanceOf(CourseAccessDeniedException.class);

        verify(courseRepository, never()).save(any());
        verify(auditTrail).record(any(AuditTrailPort.AuditEvent.class));
    }

    @Test
    void contentEditorMayModifyACourseOfAnotherUser() {
        currentUserProvider.roles = Set.of("CONTENT_EDITOR");
        Course course = Course.create("Foreign course", "Not mine", OTHER_USER_ID, CLOCK);
        when(courseRepository.findById(course.getId())).thenReturn(Optional.of(course));
        when(courseRepository.save(any(Course.class))).thenAnswer(inv -> inv.getArgument(0));

        Course result = service.rename(course.getId(), "Curated", "By an editor");

        assertThat(result.getTitle()).isEqualTo("Curated");
        verify(auditTrail, never()).record(any(AuditTrailPort.AuditEvent.class));
    }

    @Test
    void findAllShouldHideDraftsOfOtherUsers() {
        Course own = Course.create("Mine", "Draft of the caller", OWNER_ID, CLOCK);
        Course foreign = Course.create("Theirs", "Draft of somebody else", OTHER_USER_ID, CLOCK);
        when(courseRepository.findAll()).thenReturn(List.of(own, foreign));
        when(courseRepository.findManagedByOwnerId(OWNER_ID)).thenReturn(List.of(own));

        List<Course> visible = service.findAll();

        assertThat(visible).containsExactly(own);
    }

    @Test
    void getShouldRefuseADraftOfAnotherUser() {
        Course foreign = Course.create("Theirs", "Draft of somebody else", OTHER_USER_ID, CLOCK);
        when(courseRepository.findById(foreign.getId())).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.get(foreign.getId()))
                .isInstanceOf(CourseAccessDeniedException.class);
    }
    @Test
    void managedCoursesExcludeForeignPublishedAndArchivedCourses() {
        Course own = Course.create("Own", "", OWNER_ID, CLOCK);
        Course foreign = Course.create("Foreign", "", UUID.randomUUID(), CLOCK);
        UUID componentId = UUID.randomUUID();
        foreign.addComponent(componentId, CLOCK);
        when(coursePublicationPolicy.findUnpublishedComponents(List.of(componentId))).thenReturn(Set.of());
        foreign.publish(coursePublicationPolicy, CLOCK);
        when(courseRepository.findAll()).thenReturn(List.of(own, foreign));
        when(courseRepository.findManagedByOwnerId(OWNER_ID)).thenReturn(List.of(own));
        assertThat(service.findManaged()).containsExactly(own);
        foreign.archive(CLOCK);
        assertThat(service.findManaged()).containsExactly(own);
        currentUserProvider.roles = Set.of("ADMIN");
        assertThat(service.findManaged()).containsExactly(own, foreign);
    }
}
