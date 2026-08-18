package com.adventacademy.meetingservice.application.security;

import com.adventacademy.meetingservice.domain.exception.InvalidCapabilityException;
import com.adventacademy.meetingservice.domain.model.MeetingRole;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MeetingCapabilityServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-12T10:00:00Z");

    @Test
    void issuesRoomAndIdentityScopedOneTimeCapability() {
        final UUID userId = UUID.randomUUID();
        final MeetingCapabilityService service = service(NOW, 60);
        final IssuedCapability issued = service.issue("room-12345", userId, MeetingRole.HOST);

        final MeetingGrant grant = service.consume(issued.token(), "room-12345");

        assertThat(grant).isEqualTo(new MeetingGrant("room-12345", userId, MeetingRole.HOST));
        assertThatThrownBy(() -> service.consume(issued.token(), "room-12345"))
                .isInstanceOf(InvalidCapabilityException.class);
    }

    @Test
    void rejectsCrossRoomExpiredAndRevokedCapabilities() {
        final UUID userId = UUID.randomUUID();
        final MeetingCapabilityService service = service(NOW, 60);
        final IssuedCapability crossRoom = service.issue("room-12345", userId, MeetingRole.PARTICIPANT);
        assertThatThrownBy(() -> service.consume(crossRoom.token(), "room-67890"))
                .isInstanceOf(InvalidCapabilityException.class);

        final IssuedCapability revoked = service.issue("room-12345", userId, MeetingRole.PARTICIPANT);
        service.revoke("room-12345", userId);
        assertThatThrownBy(() -> service.consume(revoked.token(), "room-12345"))
                .isInstanceOf(InvalidCapabilityException.class);

        final MutableClock clock = new MutableClock(NOW);
        final MeetingCapabilityService expiringService = new MeetingCapabilityService(clock, 60, new SecureRandom());
        final IssuedCapability expired = expiringService.issue(
                "room-12345", userId, MeetingRole.PARTICIPANT);
        clock.advanceSeconds(61);
        assertThatThrownBy(() -> expiringService.consume(expired.token(), "room-12345"))
                .isInstanceOf(InvalidCapabilityException.class);
    }

    @Test
    void storesOnlyTokenDigestAndCleansExpiredEntries() {
        final MeetingCapabilityService service = service(NOW, 0);
        service.issue("room-12345", UUID.randomUUID(), MeetingRole.HOST);

        assertThat(service.activeCapabilityCount()).isZero();
    }

    private MeetingCapabilityService service(Instant now, long ttlSeconds) {
        return new MeetingCapabilityService(
                Clock.fixed(now, ZoneOffset.UTC),
                ttlSeconds,
                new SecureRandom());
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        private void advanceSeconds(long seconds) {
            instant = instant.plusSeconds(seconds);
        }
    }
}
