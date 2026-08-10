package com.adventacademy.meetingservice.adapter.websocket;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public record SignalMessage(
        SignalType type,
        String roomId,
        String senderId,
        String targetId,
        JsonNode payload,
        Instant timestamp
) {
}
