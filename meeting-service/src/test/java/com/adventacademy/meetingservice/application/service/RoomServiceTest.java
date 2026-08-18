package com.adventacademy.meetingservice.application.service;

import com.adventacademy.meetingservice.domain.exception.RoomFullException;
import com.adventacademy.meetingservice.domain.exception.RoomNotFoundException;
import com.adventacademy.meetingservice.domain.model.RoomSnapshot;
import com.adventacademy.meetingservice.domain.model.RoomStatus;
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

class RoomServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-10T10:00:00Z");

    private ExecutorService executor;
    private RoomService roomService;

    @BeforeEach
    void setUp() {
        executor = Executors.newCachedThreadPool();
        roomService = new RoomService(Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void createsAndRetrievesRoom() {
        final RoomSnapshot created = roomService.createRoom();

        final RoomSnapshot retrieved = roomService.getRoom(created.roomId().value());

        assertThat(retrieved).isEqualTo(created);
        assertThat(retrieved.status()).isEqualTo(RoomStatus.WAITING);
        assertThat(retrieved.createdAt()).isEqualTo(NOW);
    }

    @Test
    void removesRoomAfterLastParticipantLeaves() {
        final RoomSnapshot room = roomService.createRoom();
        roomService.join(room.roomId().value(), "participant-one");

        final RoomSnapshot closed = roomService.leave(room.roomId().value(), "participant-one");

        assertThat(closed.status()).isEqualTo(RoomStatus.CLOSED);
        assertThat(roomService.roomCount()).isZero();
        assertThatThrownBy(() -> roomService.getRoom(room.roomId().value()))
                .isInstanceOf(RoomNotFoundException.class);
    }

    @Test
    void concurrentJoinsNeverExceedTwoParticipants() throws Exception {
        final RoomSnapshot room = roomService.createRoom();
        roomService.join(room.roomId().value(), "participant-initial");
        final int contenders = 20;
        final CountDownLatch ready = new CountDownLatch(contenders);
        final CountDownLatch start = new CountDownLatch(1);
        final AtomicInteger successfulJoins = new AtomicInteger();
        final List<Future<?>> futures = new ArrayList<>();

        for (int index = 0; index < contenders; index++) {
            final String participantId = "participant-" + String.format("%08d", index);
            futures.add(executor.submit(() -> attemptJoin(room.roomId().value(), participantId, ready, start,
                    successfulJoins)));
        }
        ready.await();
        start.countDown();
        for (Future<?> future : futures) {
            future.get();
        }

        assertThat(successfulJoins).hasValue(1);
        assertThat(roomService.getRoom(room.roomId().value()).participants()).hasSize(2);
        assertThat(roomService.getRoom(room.roomId().value()).status()).isEqualTo(RoomStatus.ACTIVE);
    }

    private void attemptJoin(
            String roomId,
            String participantId,
            CountDownLatch ready,
            CountDownLatch start,
            AtomicInteger successfulJoins) {
        ready.countDown();
        try {
            start.await();
            roomService.join(roomId, participantId);
            successfulJoins.incrementAndGet();
        } catch (RoomFullException exception) {
            assertThat(exception.getCode()).isEqualTo("ROOM_FULL");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}
