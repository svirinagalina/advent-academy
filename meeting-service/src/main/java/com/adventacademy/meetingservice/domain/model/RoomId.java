package com.adventacademy.meetingservice.domain.model;

import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public record RoomId(String value) {

    private static final Pattern VALID_VALUE = Pattern.compile("[A-Za-z0-9_-]{8,64}");

    public RoomId {
        Objects.requireNonNull(value, "roomId must not be null");
        if (!VALID_VALUE.matcher(value).matches()) {
            throw new IllegalArgumentException("roomId must contain 8 to 64 letters, digits, underscores, or hyphens");
        }
    }

    public static RoomId generate() {
        return new RoomId(UUID.randomUUID().toString().replace("-", ""));
    }
}
