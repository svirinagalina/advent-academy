package com.adventacademy.meetingservice.application.security;

import com.adventacademy.meetingservice.domain.exception.RateLimitExceededException;
import com.adventacademy.meetingservice.infrastructure.config.MeetingProperties;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MeetingRateLimiterTest {

    @Test
    void limitsCreationCapabilityAndJoinKeysIndependently() {
        final MeetingProperties properties = new MeetingProperties();
        properties.setRoomCreationLimitPerMinute(1);
        properties.setCapabilityLimitPerMinute(1);
        properties.setJoinLimitPerMinute(1);
        final MeetingRateLimiter limiter = new MeetingRateLimiter(
                Clock.fixed(Instant.parse("2026-08-12T10:00:00Z"), ZoneOffset.UTC),
                properties);
        final UUID userId = UUID.randomUUID();

        assertThatCode(() -> limiter.requireRoomCreationAllowed(userId)).doesNotThrowAnyException();
        assertThatThrownBy(() -> limiter.requireRoomCreationAllowed(userId))
                .isInstanceOf(RateLimitExceededException.class);
        assertThatCode(() -> limiter.requireCapabilityAllowed(userId)).doesNotThrowAnyException();
        assertThatThrownBy(() -> limiter.requireCapabilityAllowed(userId))
                .isInstanceOf(RateLimitExceededException.class);
        assertThatCode(() -> limiter.requireJoinAllowed("192.0.2.1")).doesNotThrowAnyException();
        assertThatThrownBy(() -> limiter.requireJoinAllowed("192.0.2.1"))
                .isInstanceOf(RateLimitExceededException.class);
    }
}
