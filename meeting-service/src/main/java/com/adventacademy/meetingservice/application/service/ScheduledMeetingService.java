package com.adventacademy.meetingservice.application.service;

import com.adventacademy.meetingservice.application.security.MeetingCapabilityService;
import com.adventacademy.meetingservice.application.security.MeetingGrant;
import com.adventacademy.meetingservice.application.security.MeetingRateLimiter;
import com.adventacademy.meetingservice.application.session.SessionRegistration;
import com.adventacademy.meetingservice.domain.exception.MeetingAccessDeniedException;
import com.adventacademy.meetingservice.domain.exception.ScheduleException;
import com.adventacademy.meetingservice.domain.model.MeetingRole;
import jakarta.validation.constraints.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

@Service
@Transactional
public class ScheduledMeetingService {
    private final ScheduledMeetingStore store;
    private final CourseMeetingAccess courses;
    private final MeetingCapabilityService capabilities;
    private final MeetingRateLimiter limits;
    private final RoomService rooms;
    private final Clock clock;
    private final ApplicationEventPublisher events;
    public ScheduledMeetingService(ScheduledMeetingStore store, CourseMeetingAccess courses, MeetingCapabilityService capabilities,
                                   MeetingRateLimiter limits, RoomService rooms, Clock clock, ApplicationEventPublisher events) {
        this.store = store; this.courses = courses; this.capabilities = capabilities;
        this.limits = limits; this.rooms = rooms; this.clock = clock; this.events = events;
    }
    public record Create(@NotNull UUID requestId, @NotNull UUID courseId, @NotBlank @Size(max = 255) String title,
                         @NotNull Instant startsAt, @NotNull Instant endsAt,
                         @NotNull @Size(min = 1, max = 7) List<@NotNull UUID> participants) { }
    public record Edit(@NotNull @Min(0) Long version, @NotBlank @Size(max = 255) String title,
                       @NotNull Instant startsAt, @NotNull Instant endsAt,
                       @NotNull @Size(min = 1, max = 7) List<@NotNull UUID> participants) { }
    public record Version(@NotNull @Min(0) Long version) { }
    public record View(UUID id, UUID courseId, String courseTitle, UUID hostId, String title, Instant startsAt, Instant endsAt,
                       String status, long version, int participantCount, List<UUID> participants,
                       boolean canManage, boolean canStart, boolean canJoin) { }
    public record Page(List<View> items, long total, int page, int size) { }
    public record Admission(String roomId, String capability, Instant expiresAt, String role) { }
    public record Changed(String roomId) { }

    public View create(UUID actor, String authorization, Create request) {
        final CourseMeetingAccess.Course course = courses.course(request.courseId(), authorization, true);
        final String hash = hash(request);
        final List<ScheduledMeetingStore.Entry> existing = store.byRequest(actor, request.requestId());
        if (!existing.isEmpty()) {
            return retry(existing.getFirst(), hash, actor);
        }
        validateWindow(request.startsAt(), request.endsAt());
        validateParticipants(actor, request.courseId(), authorization, request.participants());
        limits.requireRoomCreationAllowed(actor);
        final UUID id = UUID.randomUUID();
        store.insert(new ScheduledMeetingStore.Entry(id, course.id(), course.title(), actor, request.requestId(), hash,
                request.title().trim(), request.startsAt(), request.endsAt(), "PLANNED", 0), clock.instant());
        final ScheduledMeetingStore.Entry saved = store.byRequest(actor, request.requestId()).getFirst();
        if (saved.id().equals(id)) {
            store.participants(id, request.participants());
        }
        return retry(saved, hash, actor);
    }

    private View retry(ScheduledMeetingStore.Entry entry, String hash, UUID actor) {
        if (!entry.creationHash().equals(hash)) {
            throw conflict("Creation request was already used with different content");
        }
        return view(entry, actor, true);
    }

    @Transactional(readOnly = true)
    public Page list(UUID actor, String authorization, boolean teacher, String tab, int page, int size) {
        if (!List.of("upcoming", "past").contains(tab)) {
            throw new IllegalArgumentException("Invalid meeting list tab");
        }
        final List<UUID> allowed = courses.courses(authorization, teacher).stream().map(CourseMeetingAccess.Course::id).toList();
        final Instant now = clock.instant();
        final List<View> entries = store.visible(actor, allowed, teacher, tab, now, page, size).stream()
                .map(entry -> view(entry, actor, teacher)).toList();
        return new Page(entries, store.visibleCount(actor, allowed, teacher, tab, now), page, size);
    }

    @Transactional(readOnly = true)
    public View detail(UUID id, UUID actor, String authorization, boolean teacher) {
        final ScheduledMeetingStore.Entry entry = store.get(id, "");
        authorize(entry, actor, authorization, teacher);
        return view(entry, actor, teacher);
    }

    public View edit(UUID id, UUID actor, String authorization, Edit request) {
        final ScheduledMeetingStore.Entry entry = store.get(id, " FOR UPDATE");
        authorize(entry, actor, authorization, true);
        if (entry.version() != request.version()) {
            final boolean same = entry.version() == request.version() + 1 && entry.title().equals(request.title().trim())
                    && entry.startsAt().equals(request.startsAt()) && entry.endsAt().equals(request.endsAt())
                    && new java.util.HashSet<>(store.participants(id)).equals(new java.util.HashSet<>(request.participants()));
            if (same && "PLANNED".equals(entry.state())) {
                return view(entry, actor, true);
            }
            throw conflict("Meeting version changed; reload it");
        }
        if (!"PLANNED".equals(effective(entry))) {
            throw conflict("Only a planned meeting can be rescheduled");
        }
        validateWindow(request.startsAt(), request.endsAt());
        validateParticipants(entry.hostId(), entry.courseId(), authorization, request.participants());
        store.reschedule(entry, request.title().trim(), request.startsAt(), request.endsAt());
        store.participants(id, request.participants());
        events.publishEvent(new Changed(roomId(id)));
        return view(store.get(id, ""), actor, true);
    }

    public View transition(UUID id, UUID actor, String authorization, long version, String action) {
        final ScheduledMeetingStore.Entry entry = store.get(id, " FOR UPDATE");
        authorize(entry, actor, authorization, true);
        final String target = target(action);
        if (entry.version() != version) {
            if (entry.version() == version + 1 && entry.state().equals(target)) {
                return view(entry, actor, true);
            }
            throw conflict("Meeting version changed; reload it");
        }
        final String state = effective(entry);
        validateTransition(state, action, entry);
        store.transition(id, target);
        events.publishEvent(new Changed(roomId(id)));
        return view(store.get(id, ""), actor, true);
    }

    public Admission issue(UUID id, UUID actor, String authorization) {
        final ScheduledMeetingStore.Entry entry = store.get(id, " FOR SHARE");
        final boolean host = entry.hostId().equals(actor);
        authorize(entry, actor, authorization, host);
        requireOpen(entry);
        limits.requireCapabilityAllowed(actor);
        rooms.ensureRoom(roomId(id), store.participants(id).size() + 1);
        final MeetingRole role;
        if (host) {
            role = MeetingRole.HOST;
        } else {
            role = MeetingRole.PARTICIPANT;
        }
        final var issued = capabilities.issueScheduled(roomId(id), actor, role, entry.version(), authorization);
        return new Admission(roomId(id), issued.token(), issued.expiresAt(), role.name());
    }

    public SessionRegistration join(MeetingGrant grant, Supplier<SessionRegistration> register) {
        final ScheduledMeetingStore.Entry entry = store.get(meetingId(grant.roomId()), " FOR SHARE");
        requireOpen(entry);
        if (grant.scheduleVersion() == null || entry.version() != grant.scheduleVersion()) {
            throw conflict("Meeting version changed; reconnect");
        }
        authorize(entry, grant.userId(), grant.authorization(), grant.role() == MeetingRole.HOST);
        rooms.ensureRoom(grant.roomId(), store.participants(entry.id()).size() + 1);
        return register.get();
    }

    @Transactional(readOnly = true)
    public void requireOpen(MeetingGrant grant) {
        final ScheduledMeetingStore.Entry entry = store.get(meetingId(grant.roomId()), "");
        requireOpen(entry);
        if (grant.scheduleVersion() == null || entry.version() != grant.scheduleVersion()) {
            throw conflict("Meeting version changed; reconnect");
        }
    }

    @Scheduled(fixedDelayString = "${meeting.schedule-sweep-ms:1000}")
    public void expire() {
        for (UUID id : store.elapsed(clock.instant())) {
            final ScheduledMeetingStore.Entry entry = store.get(id, " FOR UPDATE");
            if (List.of("PLANNED", "ACTIVE").contains(entry.state()) && !entry.endsAt().isAfter(clock.instant())) {
                store.transition(id, effective(entry));
                events.publishEvent(new Changed(roomId(id)));
            }
        }
    }

    private void authorize(ScheduledMeetingStore.Entry entry, UUID actor, String authorization, boolean teacher) {
        courses.course(entry.courseId(), authorization, teacher);
        if (!teacher && !store.participants(entry.id()).contains(actor)) {
            throw new MeetingAccessDeniedException();
        }
    }

    private View view(ScheduledMeetingStore.Entry entry, UUID actor, boolean teacher) {
        final String state = effective(entry);
        final boolean inWindow = !clock.instant().isBefore(entry.startsAt().minusSeconds(600)) && clock.instant().isBefore(entry.endsAt());
        final List<UUID> participants = store.participants(entry.id());
        final boolean admitted = entry.hostId().equals(actor) || participants.contains(actor);
        final List<UUID> visibleParticipants;
        if (teacher) {
            visibleParticipants = participants;
        } else {
            visibleParticipants = List.of();
        }
        return new View(entry.id(), entry.courseId(), entry.courseTitle(), entry.hostId(), entry.title(), entry.startsAt(), entry.endsAt(),
                state, entry.version(), participants.size() + 1, visibleParticipants, teacher,
                teacher && "PLANNED".equals(state) && inWindow, admitted && "ACTIVE".equals(state) && inWindow);
    }

    private void requireOpen(ScheduledMeetingStore.Entry entry) {
        if (!"ACTIVE".equals(effective(entry)) || clock.instant().isBefore(entry.startsAt().minusSeconds(600))) {
            throw conflict("Meeting is not open for joining");
        }
    }
    private String effective(ScheduledMeetingStore.Entry entry) {
        if (!entry.endsAt().isAfter(clock.instant()) && "PLANNED".equals(entry.state())) {
            return "EXPIRED";
        }
        if (!entry.endsAt().isAfter(clock.instant()) && "ACTIVE".equals(entry.state())) {
            return "FINISHED";
        }
        return entry.state();
    }
    private void validateWindow(Instant start, Instant end) {
        if (!end.isAfter(start) || !end.isAfter(clock.instant()) || start.isBefore(clock.instant().minusSeconds(600))
                || Duration.between(start, end).compareTo(Duration.ofHours(8)) > 0) {
            throw new IllegalArgumentException("Use a valid future meeting window of at most eight hours");
        }
    }
    private void validateParticipants(UUID host, UUID courseId, String authorization, List<UUID> participants) {
        if (participants.size() != new java.util.HashSet<>(participants).size() || participants.contains(host)
                || !courses.students(courseId, authorization).containsAll(participants)) {
            throw new IllegalArgumentException("Select distinct enrolled students other than the meeting host");
        }
    }
    private String target(String action) {
        return switch (action) {
            case "start" -> "ACTIVE";
            case "finish" -> "FINISHED";
            case "cancel" -> "CANCELLED";
            default -> throw new IllegalArgumentException("Unknown meeting action");
        };
    }
    private void validateTransition(String state, String action, ScheduledMeetingStore.Entry entry) {
        final boolean valid = switch (action) {
            case "start" -> "PLANNED".equals(state) && !clock.instant().isBefore(entry.startsAt().minusSeconds(600));
            case "finish" -> "ACTIVE".equals(state);
            case "cancel" -> List.of("PLANNED", "ACTIVE").contains(state);
            default -> false;
        };
        if (!valid) {
            throw conflict("This meeting transition is not available");
        }
    }
    private String hash(Create request) {
        final String value = request.courseId() + "\n" + request.title().trim() + "\n" + request.startsAt() + "\n" + request.endsAt()
                + "\n" + request.participants().stream().sorted().map(UUID::toString).collect(java.util.stream.Collectors.joining(","));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
    private ScheduleException conflict(String message) {
        return new ScheduleException("MEETING_CONFLICT", message);
    }
    public static String roomId(UUID id) {
        return "s_" + id.toString().replace("-", "");
    }
    private UUID meetingId(String room) {
        final String value = room.substring(2);
        return UUID.fromString(value.substring(0, 8) + "-" + value.substring(8, 12) + "-" + value.substring(12, 16)
                + "-" + value.substring(16, 20) + "-" + value.substring(20));
    }
}
