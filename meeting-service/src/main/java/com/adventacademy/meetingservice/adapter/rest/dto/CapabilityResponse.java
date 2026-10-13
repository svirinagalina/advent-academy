package com.adventacademy.meetingservice.adapter.rest.dto;

import com.adventacademy.meetingservice.application.security.IssuedCapability;

import java.time.Instant;

public record CapabilityResponse(String capability, Instant expiresAt, String role) {

    public static CapabilityResponse from(IssuedCapability issued) {
        return new CapabilityResponse(issued.token(), issued.expiresAt(), issued.role().name());
    }
}
