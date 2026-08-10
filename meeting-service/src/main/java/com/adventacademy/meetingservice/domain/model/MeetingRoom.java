package com.adventacademy.meetingservice.domain.model;

import com.adventacademy.meetingservice.domain.exception.DuplicateParticipantException;
import com.adventacademy.meetingservice.domain.exception.RoomClosedException;
import com.adventacademy.meetingservice.domain.exception.RoomFullException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

public final class MeetingRoom {

    public static final int MAX_PARTICIPANTS = 2;

    private final RoomId roomId;
    private final Instant createdAt;
    private final Set<ParticipantId> participants = new LinkedHashSet<>();
    private RoomStatus status = RoomStatus.WAITING;

    public MeetingRoom(RoomId roomId, Instant createdAt) {
        this.roomId = roomId;
        this.createdAt = createdAt;
    }

    public synchronized RoomJoinResult join(ParticipantId participantId) {
        ensureOpen();
        if (participants.contains(participantId)) {
            throw new DuplicateParticipantException(participantId.value());
        }
        if (participants.size() >= MAX_PARTICIPANTS) {
            throw new RoomFullException(roomId.value());
        }

        final Optional<ParticipantId> peerId = participants.stream().findFirst();
        participants.add(participantId);
        if (participants.size() == MAX_PARTICIPANTS) {
            transitionTo(RoomStatus.ACTIVE);
        }
        return new RoomJoinResult(snapshot(), peerId);
    }

    public synchronized boolean leave(ParticipantId participantId) {
        final boolean removed = participants.remove(participantId);
        if (!removed) {
            return false;
        }
        if (participants.isEmpty()) {
            transitionTo(RoomStatus.CLOSED);
        } else if (status == RoomStatus.ACTIVE) {
            transitionTo(RoomStatus.WAITING);
        }
        return true;
    }

    public synchronized void close() {
        if (!participants.isEmpty()) {
            throw new IllegalStateException("A room with active participants cannot be closed directly");
        }
        transitionTo(RoomStatus.CLOSED);
    }

    public synchronized RoomSnapshot snapshot() {
        return new RoomSnapshot(roomId, status, createdAt, new ArrayList<>(participants), MAX_PARTICIPANTS);
    }

    private void ensureOpen() {
        if (status == RoomStatus.CLOSED) {
            throw new RoomClosedException(roomId.value());
        }
    }

    private void transitionTo(RoomStatus nextStatus) {
        final boolean valid = switch (status) {
            case WAITING -> nextStatus == RoomStatus.ACTIVE || nextStatus == RoomStatus.CLOSED;
            case ACTIVE -> nextStatus == RoomStatus.WAITING || nextStatus == RoomStatus.CLOSED;
            case CLOSED -> false;
        };
        if (!valid) {
            throw new IllegalStateException("Invalid room transition: " + status + " -> " + nextStatus);
        }
        status = nextStatus;
    }
}
