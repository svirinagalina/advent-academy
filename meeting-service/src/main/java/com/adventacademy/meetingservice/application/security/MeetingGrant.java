package com.adventacademy.meetingservice.application.security;

import com.adventacademy.meetingservice.domain.model.MeetingRole;

import java.util.UUID;

public record MeetingGrant(String roomId, UUID userId, MeetingRole role) {
}
