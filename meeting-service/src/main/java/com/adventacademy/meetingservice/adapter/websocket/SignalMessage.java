package com.adventacademy.meetingservice.adapter.websocket;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public record SignalMessage(
        int version,
        SignalType type,
        String roomId,
        String capability,
        String senderId,
        String targetId,
        JsonNode payload,
        Instant timestamp
) {
}
