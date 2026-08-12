package com.adventacademy.meetingservice.application.security;

import com.adventacademy.meetingservice.domain.model.MeetingRole;

import java.time.Instant;

public record IssuedCapability(String token, Instant expiresAt, MeetingRole role) {
}
