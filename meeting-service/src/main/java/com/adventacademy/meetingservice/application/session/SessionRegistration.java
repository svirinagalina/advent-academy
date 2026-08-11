package com.adventacademy.meetingservice.application.session;

import com.adventacademy.meetingservice.domain.model.RoomSnapshot;

import java.util.Optional;

public record SessionRegistration(
        MeetingSession session,
        RoomSnapshot room,
        Optional<MeetingSession> peer,
        boolean newlyJoined
) {
}
