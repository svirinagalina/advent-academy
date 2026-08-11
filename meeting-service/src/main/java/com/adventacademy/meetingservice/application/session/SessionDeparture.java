package com.adventacademy.meetingservice.application.session;

import com.adventacademy.meetingservice.domain.model.RoomSnapshot;

public record SessionDeparture(MeetingSession session, RoomSnapshot room) {
}
