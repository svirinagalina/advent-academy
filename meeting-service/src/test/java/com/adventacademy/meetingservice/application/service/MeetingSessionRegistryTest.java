package com.adventacademy.meetingservice.application.service;

import com.adventacademy.meetingservice.application.session.SessionRegistration;
import com.adventacademy.meetingservice.application.session.SessionSignalType;
import com.adventacademy.meetingservice.domain.exception.InvalidSignalException;
import com.adventacademy.meetingservice.domain.exception.RoomFullException;
import com.adventacademy.meetingservice.domain.model.RoomSnapshot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
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
    void assignsServerIdentityAndMakesDuplicateJoinIdempotent() {
        final RoomSnapshot room = roomService.createRoom();

        final SessionRegistration first = registry.join("session-a", room.roomId().value());
        final SessionRegistration duplicate = registry.join("session-a", room.roomId().value());

        assertThat(first.session().participantId()).matches("[a-f0-9]{32}");
        assertThat(first.newlyJoined()).isTrue();
        assertThat(duplicate.newlyJoined()).isFalse();
        assertThat(duplicate.session()).isEqualTo(first.session());
        assertThat(registry.sessionCount()).isEqualTo(1);
        assertThat(roomService.getRoom(room.roomId().value()).participants()).hasSize(1);
    }

    @Test
    void concurrentSessionJoinsNeverOverfillLastSlot() throws Exception {
        final RoomSnapshot room = roomService.createRoom();
        registry.join("session-initial", room.roomId().value());
        final int contenders = 20;
        final CountDownLatch ready = new CountDownLatch(contenders);
        final CountDownLatch start = new CountDownLatch(1);
        final AtomicInteger successfulJoins = new AtomicInteger();
        final List<Future<?>> futures = new ArrayList<>();

        for (int index = 0; index < contenders; index++) {
            final String sessionId = "session-" + index;
            futures.add(executor.submit(() -> attemptJoin(
                    sessionId, room.roomId().value(), ready, start, successfulJoins)));
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
        final SessionRegistration registration = registry.join("session-a", room.roomId().value());

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
        final SessionRegistration first = registry.join("session-a", room.roomId().value());
        final SessionRegistration second = registry.join("session-b", room.roomId().value());

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
            CountDownLatch ready,
            CountDownLatch start,
            AtomicInteger successfulJoins) {
        ready.countDown();
        try {
            start.await();
            registry.join(sessionId, roomId);
            successfulJoins.incrementAndGet();
        } catch (RoomFullException exception) {
            assertThat(exception.getCode()).isEqualTo("ROOM_FULL");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}
