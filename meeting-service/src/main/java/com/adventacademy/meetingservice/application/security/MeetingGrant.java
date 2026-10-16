package com.adventacademy.meetingservice.application.security;

import com.adventacademy.meetingservice.domain.model.MeetingRole;

import java.util.UUID;

public record MeetingGrant(String roomId, UUID userId, MeetingRole role, Long scheduleVersion, String authorization) {
    public MeetingGrant(String roomId, UUID userId, MeetingRole role) {
        this(roomId, userId, role, null, null);
    }
    @Override
    public String toString() {
        return "MeetingGrant[roomId=" + roomId + ", userId=" + userId + ", role=" + role + ", scheduleVersion=" + scheduleVersion + "]";
    }
}
