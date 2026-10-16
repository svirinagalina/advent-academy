package com.adventacademy.meetingservice.application.service;

import com.adventacademy.meetingservice.application.security.MeetingCapabilityService;
import com.adventacademy.meetingservice.application.session.SessionSignalType;
import com.adventacademy.meetingservice.domain.exception.MeetingAccessDeniedException;
import com.adventacademy.meetingservice.domain.exception.ScheduleException;
import com.adventacademy.meetingservice.domain.exception.InvalidSignalException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ScheduledMeetingServiceTest {
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private com.adventacademy.security.AccessTokenStatusClient accessTokenStatusClient;

    private static final PostgreSQLContainer DATABASE = new PostgreSQLContainer("postgres:16-alpine");
    static {
        DATABASE.start();
    }
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", DATABASE::getJdbcUrl);
        properties.add("spring.datasource.username", DATABASE::getUsername);
        properties.add("spring.datasource.password", DATABASE::getPassword);
        properties.add("meeting.schedule-sweep-ms", () -> "3600000");
    }
    @Autowired private org.springframework.boot.test.web.client.TestRestTemplate http;
    @Autowired private ScheduledMeetingService service;
    @Autowired private ScheduledMeetingStore store;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MeetingCapabilityService capabilities;
    @Autowired private MeetingSessionRegistry sessions;
    @MockitoBean private CourseMeetingAccess courses;
    private UUID host;
    private UUID course;
    private List<UUID> students;
    private Instant start;
    private Instant end;

    @BeforeEach
    void setup() {
        jdbc.update("DELETE FROM meeting.scheduled_meetings");
        host = UUID.randomUUID(); course = UUID.randomUUID();
        students = List.of(UUID.randomUUID(), UUID.randomUUID());
        start = Instant.now().truncatedTo(ChronoUnit.MILLIS).minusSeconds(60);
        end = start.plusSeconds(3600);
        when(courses.course(course, "teacher", true)).thenReturn(new CourseMeetingAccess.Course(course, "Course"));
        when(courses.course(course, "student", false)).thenReturn(new CourseMeetingAccess.Course(course, "Course"));
        when(courses.students(course, "teacher")).thenReturn(students);
        when(courses.courses("teacher", true)).thenReturn(List.of(new CourseMeetingAccess.Course(course, "Course")));
        when(courses.courses("student", false)).thenReturn(List.of(new CourseMeetingAccess.Course(course, "Course")));
    }
    private ScheduledMeetingService.Create request() {
        return new ScheduledMeetingService.Create(UUID.randomUUID(), course, "Group", start, end, students);
    }
    private ScheduledMeetingService.View planned() {
        return service.create(host, "teacher", request());
    }
    @Test
    void realHttpBoundaryUsesSignedInstructorRolesAndRejectsStudentMutations() {
        final String token = com.adventacademy.meetingservice.support.TestAccessTokens.accessToken(host, List.of("INSTRUCTOR"));
        when(courses.course(course, "Bearer " + token, true)).thenReturn(new CourseMeetingAccess.Course(course, "Course"));
        when(courses.students(course, "Bearer " + token)).thenReturn(students);
        final var headers = new org.springframework.http.HttpHeaders();
        headers.setBearerAuth(token);
        final var created = http.exchange("/api/meetings/scheduled", org.springframework.http.HttpMethod.POST,
                new org.springframework.http.HttpEntity<>(request(), headers), ScheduledMeetingService.View.class);
        assertThat(created.getStatusCode().value()).isEqualTo(200);
        assertThat(created.getBody()).isNotNull();
        headers.setBearerAuth(com.adventacademy.meetingservice.support.TestAccessTokens.accessToken(students.getFirst(), List.of("STUDENT")));
        final var denied = http.exchange("/api/meetings/scheduled", org.springframework.http.HttpMethod.POST,
                new org.springframework.http.HttpEntity<>(request(), headers), String.class);
        assertThat(denied.getStatusCode().value()).isEqualTo(403);
        headers.setBearerAuth(token);
        final var detail = http.exchange("/api/meetings/scheduled/" + created.getBody().id(), org.springframework.http.HttpMethod.GET,
                new org.springframework.http.HttpEntity<>(headers), ScheduledMeetingService.View.class);
        assertThat(detail.getStatusCode().value()).isEqualTo(200);
        assertThat(detail.getBody()).isNotNull();
        assertThat(detail.getBody().participants()).hasSize(2);
    }

    @Test
    void migrationAndConcurrentCreationKeepOneMeetingWithAllMembers() throws Exception {
        final var request = request();
        try (var executor = Executors.newFixedThreadPool(2)) {
            final var first = executor.submit(() -> service.create(host, "teacher", request));
            final var second = executor.submit(() -> service.create(host, "teacher", request));
            assertThat(first.get(10, TimeUnit.SECONDS).id()).isEqualTo(second.get(10, TimeUnit.SECONDS).id());
        }
        assertThat(service.list(host, "teacher", true, "upcoming", 0, 20).total()).isEqualTo(1);
        assertThat(store.participants(store.byRequest(host, request.requestId()).getFirst().id())).containsExactlyInAnyOrderElementsOf(students);
    }
    @Test
    void creationRetrySurvivesReschedulingButRejectsChangedContent() {
        final var request = request();
        final var first = service.create(host, "teacher", request);
        service.edit(first.id(), host, "teacher", new ScheduledMeetingService.Edit(0L, "Changed", start.plusSeconds(60), end, students));
        assertThat(service.create(host, "teacher", request).title()).isEqualTo("Changed");
        final var changed = new ScheduledMeetingService.Create(request.requestId(), course, "Different", start, end, students);
        assertThatThrownBy(() -> service.create(host, "teacher", changed)).isInstanceOf(ScheduleException.class);
    }
    @Test
    void optimisticUpdatesAcceptExactRetryAndRejectCompetingDraft() {
        final var first = planned();
        final var edit = new ScheduledMeetingService.Edit(0L, "Moved", start.plusSeconds(60), end, students);
        assertThat(service.edit(first.id(), host, "teacher", edit).version()).isEqualTo(1);
        assertThat(service.edit(first.id(), host, "teacher", edit).version()).isEqualTo(1);
        assertThatThrownBy(() -> service.edit(first.id(), host, "teacher",
                new ScheduledMeetingService.Edit(0L, "Other", start, end, students))).isInstanceOf(ScheduleException.class);
    }
    @Test
    void onlyEnrolledSelectedStudentsCanReadAndJoin() {
        final var first = planned();
        assertThatThrownBy(() -> service.issue(first.id(), students.getFirst(), "student")).isInstanceOf(ScheduleException.class);
        service.transition(first.id(), host, "teacher", 0, "start");
        assertThatThrownBy(() -> service.issue(first.id(), UUID.randomUUID(), "student")).isInstanceOf(MeetingAccessDeniedException.class);
        assertThat(service.detail(first.id(), students.getFirst(), "student", false).participants()).isEmpty();
        assertThat(service.list(UUID.randomUUID(), "student", false, "upcoming", 0, 20).items()).isEmpty();
        when(courses.course(course, "denied", true)).thenThrow(new MeetingAccessDeniedException());
        assertThatThrownBy(() -> service.transition(first.id(), UUID.randomUUID(), "denied", 1, "cancel"))
                .isInstanceOf(MeetingAccessDeniedException.class);
    }
    @Test
    void cancellationMakesPreviouslyIssuedAndConsumedAdmissionUnusable() {
        final var first = planned();
        service.transition(first.id(), host, "teacher", 0, "start");
        final var admission = service.issue(first.id(), students.getFirst(), "student");
        final var grant = capabilities.consume(admission.capability(), admission.roomId());
        service.transition(first.id(), host, "teacher", 1, "cancel");
        assertThatThrownBy(() -> service.join(grant, () -> sessions.join("cancelled", grant))).isInstanceOf(ScheduleException.class);
        assertThatThrownBy(() -> service.issue(first.id(), students.getFirst(), "student")).isInstanceOf(ScheduleException.class);
    }
    @Test
    void groupNegotiatesEveryPairAndRecreatesEmptyRoom() {
        final var first = planned();
        service.transition(first.id(), host, "teacher", 0, "start");
        final var hostAdmission = service.issue(first.id(), host, "teacher");
        final var hostGrant = capabilities.consume(hostAdmission.capability(), hostAdmission.roomId());
        final var hostSession = service.join(hostGrant, () -> sessions.join("host-" + first.id(), hostGrant));
        for (int index = 0; index < students.size(); index++) {
            final String connection = "student-" + index + first.id();
            final var admission = service.issue(first.id(), students.get(index), "student");
            final var grant = capabilities.consume(admission.capability(), admission.roomId());
            final var joined = service.join(grant, () -> sessions.join(connection, grant));
            assertThat(sessions.requireRoute(hostSession.session().sessionId(), admission.roomId(),
                    joined.session().participantId(), SessionSignalType.OFFER)).isNotNull();
            assertThatThrownBy(() -> sessions.requireRoute(connection, admission.roomId(),
                    hostSession.session().participantId(), SessionSignalType.OFFER)).isInstanceOf(InvalidSignalException.class);
        }
        sessions.leave(hostSession.session().sessionId());
        sessions.leave("student-0" + first.id()); sessions.leave("student-1" + first.id());
        assertThat(service.join(hostGrant, () -> sessions.join("again-" + first.id(), hostGrant)).room().participants()).hasSize(1);
        sessions.leave("again-" + first.id());
    }
    @Test
    void joinHoldsScheduleLockUntilRegistrationSoCancellationCannotOvertakeIt() throws Exception {
        final var first = planned();
        service.transition(first.id(), host, "teacher", 0, "start");
        final var admission = service.issue(first.id(), students.getFirst(), "student");
        final var grant = capabilities.consume(admission.capability(), admission.roomId());
        final var entered = new CountDownLatch(1);
        final var release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            final var join = executor.submit(() -> service.join(grant, () -> holdRegistration(grant, entered, release)));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            final var cancel = executor.submit(() -> service.transition(first.id(), host, "teacher", 1, "cancel"));
            assertThatThrownBy(() -> cancel.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(java.util.concurrent.TimeoutException.class);
            release.countDown(); join.get(5, TimeUnit.SECONDS);
            assertThat(cancel.get(5, TimeUnit.SECONDS).status()).isEqualTo("CANCELLED");
        } finally {
            release.countDown(); sessions.leave("race-" + first.id());
        }
    }
    private com.adventacademy.meetingservice.application.session.SessionRegistration holdRegistration(
            com.adventacademy.meetingservice.application.security.MeetingGrant grant, CountDownLatch entered, CountDownLatch release) {
        entered.countDown();
        try {
            if (!release.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Registration timed out");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt(); throw new IllegalStateException(exception);
        }
        return sessions.join("race-" + UUID.fromString(grant.roomId().substring(2).replaceFirst(
                "(.{8})(.{4})(.{4})(.{4})(.{12})", "$1-$2-$3-$4-$5")), grant);
    }
    @Test
    void naturalExpiryClosesActiveScheduleAndPreservesHistory() {
        final var first = planned();
        service.transition(first.id(), host, "teacher", 0, "start");
        jdbc.update("UPDATE meeting.scheduled_meetings SET starts_at=?,ends_at=? WHERE id=?",
                java.sql.Timestamp.from(Instant.now().minusSeconds(120)), java.sql.Timestamp.from(Instant.now().minusSeconds(1)), first.id());
        service.expire();
        assertThat(store.get(first.id(), "").state()).isEqualTo("FINISHED");
        assertThat(service.list(host, "teacher", true, "past", 0, 20).total()).isEqualTo(1);
    }
}
