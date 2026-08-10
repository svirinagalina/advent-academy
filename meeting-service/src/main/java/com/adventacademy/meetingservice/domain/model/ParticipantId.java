package com.adventacademy.meetingservice.domain.model;

import java.util.Objects;
import java.util.regex.Pattern;

public record ParticipantId(String value) {

    private static final Pattern VALID_VALUE = Pattern.compile("[A-Za-z0-9_-]{8,64}");

    public ParticipantId {
        Objects.requireNonNull(value, "participantId must not be null");
        if (!VALID_VALUE.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "participantId must contain 8 to 64 letters, digits, underscores, or hyphens");
        }
    }
}
