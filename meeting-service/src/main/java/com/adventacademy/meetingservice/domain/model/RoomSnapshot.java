package com.adventacademy.meetingservice.domain.model;

import java.time.Instant;
import java.util.List;

public record RoomSnapshot(
        RoomId roomId,
        RoomStatus status,
        Instant createdAt,
        List<ParticipantId> participants,
        int maxParticipants
) {
}
