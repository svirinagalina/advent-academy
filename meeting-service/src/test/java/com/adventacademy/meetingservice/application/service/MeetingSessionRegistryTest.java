package com.adventacademy.meetingservice.application.service;

import com.adventacademy.meetingservice.application.session.SessionRegistration;
import com.adventacademy.meetingservice.application.session.SessionSignalType;
import com.adventacademy.meetingservice.application.security.MeetingGrant;
import com.adventacademy.meetingservice.domain.exception.InvalidSignalException;
import com.adventacademy.meetingservice.domain.exception.RoomFullException;
import com.adventacademy.meetingservice.domain.model.RoomSnapshot;
import com.adventacademy.meetingservice.domain.model.MeetingRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MeetingSessionRegistryTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-11T10:00:00Z"), ZoneOffset.UTC);

    private ExecutorService executor;
    private RoomService roomService;
    private MeetingSessionRegistry registry;

    @BeforeEach
    void setUp() {
        executor = Executors.newCachedThreadPool();
        roomService = new RoomService(CLOCK);
        registry = new MeetingSessionRegistry(roomService);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void groupCapacityIsEightAndEveryEarlierParticipantMayOfferToALaterOne() {
        final String room = "group-room-eight";
        roomService.ensureRoom(room, 8);
        final List<SessionRegistration> participants = new ArrayList<>();
        for (int index = 0; index < 8; index++) {
            participants.add(registry.join("group-" + index, grant(room, index + 1)));
        }
        assertThatThrownBy(() -> registry.join("extra", grant(room, 9))).isInstanceOf(RoomFullException.class);
        for (int sender = 0; sender < 8; sender++) {
            for (int target = sender + 1; target < 8; target++) {
                final String targetId = participants.get(target).session().participantId();
                assertThat(registry.requireRoute("group-" + sender, room, targetId, SessionSignalType.OFFER)).isNotNull();
            }
        }
        registry.leave("group-0");
        assertThat(registry.current("group-1").room().participants()).hasSize(7);
        assertThat(registry.current("group-1").room().status().name()).isEqualTo("ACTIVE");
    }

    @Test
    void assignsServerIdentityAndMakesDuplicateJoinIdempotent() {
        final RoomSnapshot room = roomService.createRoom();

        final MeetingGrant grant = grant(room.roomId().value(), 1);
        final SessionRegistration first = registry.join("session-a", grant);
        final SessionRegistration duplicate = registry.join("session-a", grant);

        assertThat(first.session().participantId()).matches("participant-[A-Za-z0-9_-]{22}");
        assertThat(first.session().userId()).isEqualTo(grant.userId());
        assertThat(first.newlyJoined()).isTrue();
        assertThat(duplicate.newlyJoined()).isFalse();
        assertThat(duplicate.session()).isEqualTo(first.session());
        assertThat(registry.sessionCount()).isEqualTo(1);
        assertThat(roomService.getRoom(room.roomId().value()).participants()).hasSize(1);
    }

    @Test
    void concurrentSessionJoinsNeverOverfillLastSlot() throws Exception {
        final RoomSnapshot room = roomService.createRoom();
        registry.join("session-initial", grant(room.roomId().value(), 1));
        final int contenders = 20;
        final CountDownLatch ready = new CountDownLatch(contenders);
        final CountDownLatch start = new CountDownLatch(1);
        final AtomicInteger successfulJoins = new AtomicInteger();
        final List<Future<?>> futures = new ArrayList<>();

        for (int index = 0; index < contenders; index++) {
            final String sessionId = "session-" + index;
            final int userIndex = index + 2;
            futures.add(executor.submit(() -> attemptJoin(
                    sessionId, room.roomId().value(), userIndex, ready, start, successfulJoins)));
        }
        ready.await();
        start.countDown();
        for (Future<?> future : futures) {
            future.get();
        }

        assertThat(successfulJoins).hasValue(1);
        assertThat(registry.sessionCount()).isEqualTo(2);
        assertThat(roomService.getRoom(room.roomId().value()).participants()).hasSize(2);
    }

    @Test
    void leaveIsIdempotentAndRemovesBothRegistryIndexes() {
        final RoomSnapshot room = roomService.createRoom();
        final SessionRegistration registration = registry.join(
                "session-a", grant(room.roomId().value(), 1));

        assertThat(registry.leave("session-a")).isPresent();
        assertThat(registry.leave("session-a")).isEmpty();

        assertThat(registry.findBySession("session-a")).isEmpty();
        assertThat(registry.findByParticipant(registration.session().participantId())).isEmpty();
        assertThat(registry.sessionCount()).isZero();
        assertThat(roomService.roomCount()).isZero();
    }

    @Test
    void enforcesServerSelectedOfferAndAnswerRoles() {
        final RoomSnapshot room = roomService.createRoom();
        final SessionRegistration first = registry.join("session-a", grant(room.roomId().value(), 1));
        final SessionRegistration second = registry.join("session-b", grant(room.roomId().value(), 2));

        assertThat(registry.requireRoute(
                "session-a",
                room.roomId().value(),
                second.session().participantId(),
                SessionSignalType.OFFER).sender()).isEqualTo(first.session());
        assertThat(registry.requireRoute(
                "session-b",
                room.roomId().value(),
                first.session().participantId(),
                SessionSignalType.ANSWER).sender()).isEqualTo(second.session());

        assertThatThrownBy(() -> registry.requireRoute(
                "session-b",
                room.roomId().value(),
                first.session().participantId(),
                SessionSignalType.OFFER))
                .isInstanceOf(InvalidSignalException.class)
                .extracting(exception -> ((InvalidSignalException) exception).getCode())
                .isEqualTo("INVALID_NEGOTIATION_ROLE");
    }

    private void attemptJoin(
            String sessionId,
            String roomId,
            int userIndex,
            CountDownLatch ready,
            CountDownLatch start,
            AtomicInteger successfulJoins) {
        ready.countDown();
        try {
            start.await();
            registry.join(sessionId, grant(roomId, userIndex));
            successfulJoins.incrementAndGet();
        } catch (RoomFullException exception) {
            assertThat(exception.getCode()).isEqualTo("ROOM_FULL");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private MeetingGrant grant(String roomId, int userIndex) {
        MeetingRole role = MeetingRole.PARTICIPANT;
        if (userIndex == 1) {
            role = MeetingRole.HOST;
        }
        return new MeetingGrant(
                roomId,
                new UUID(0, userIndex),
                role);
    }
}
