package com.adventacademy.meetingservice.adapter.rest.dto;

import com.adventacademy.meetingservice.domain.model.RoomSnapshot;

import java.time.Instant;

public record RoomResponse(
        String roomId,
        String status,
        int maxParticipants,
        int participantCount,
        Instant createdAt
) {

    public static RoomResponse from(RoomSnapshot room) {
        return new RoomResponse(
                room.roomId().value(),
                room.status().name(),
                room.maxParticipants(),
                room.participants().size(),
                room.createdAt());
    }
}
