package com.adventacademy.meetingservice.domain.model;

import java.util.Optional;

public record RoomJoinResult(RoomSnapshot room, Optional<ParticipantId> peerId) {
}
