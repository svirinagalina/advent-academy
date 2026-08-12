package com.adventacademy.meetingservice.application.security;

import com.adventacademy.meetingservice.domain.exception.RateLimitExceededException;
import com.adventacademy.meetingservice.infrastructure.config.MeetingProperties;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class MeetingRateLimiter {

    private static final long WINDOW_SECONDS = 60;

    private final Map<String, Window> roomCreationWindows = new HashMap<>();
    private final Map<String, Window> capabilityWindows = new HashMap<>();
    private final Map<String, Window> joinWindows = new HashMap<>();
    private final Clock clock;
    private final int roomCreationLimit;
    private final int capabilityLimit;
    private final int joinLimit;

    public MeetingRateLimiter(Clock clock, MeetingProperties properties) {
        this.clock = clock;
        this.roomCreationLimit = properties.getRoomCreationLimitPerMinute();
        this.capabilityLimit = properties.getCapabilityLimitPerMinute();
        this.joinLimit = properties.getJoinLimitPerMinute();
    }

    public synchronized void requireRoomCreationAllowed(UUID userId) {
        requireAllowed(roomCreationWindows, userId.toString(), roomCreationLimit);
    }

    public synchronized void requireCapabilityAllowed(UUID userId) {
        requireAllowed(capabilityWindows, userId.toString(), capabilityLimit);
    }

    public synchronized void requireJoinAllowed(String remoteKey) {
        requireAllowed(joinWindows, remoteKey, joinLimit);
    }

    private void requireAllowed(Map<String, Window> windows, String key, int limit) {
        final Instant now = clock.instant();
        windows.values().removeIf(existing -> !now.isBefore(
                existing.startedAt().plusSeconds(WINDOW_SECONDS)));
        Window window = windows.get(key);
        if (window == null || !now.isBefore(window.startedAt().plusSeconds(WINDOW_SECONDS))) {
            window = new Window(now, 0);
        }
        if (window.count() >= limit) {
            throw new RateLimitExceededException();
        }
        windows.put(key, new Window(window.startedAt(), window.count() + 1));
    }

    private record Window(Instant startedAt, int count) {
    }
}
