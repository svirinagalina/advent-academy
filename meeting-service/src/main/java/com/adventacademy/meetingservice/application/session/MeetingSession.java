package com.adventacademy.meetingservice.application.session;

import com.adventacademy.meetingservice.domain.model.MeetingRole;

import java.util.UUID;

public record MeetingSession(
        String sessionId,
        String participantId,
        String roomId,
        UUID userId,
        MeetingRole role) {
}
