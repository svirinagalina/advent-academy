package com.adventacademy.meetingservice.application.security;

import com.adventacademy.meetingservice.application.service.RoomService;
import com.adventacademy.meetingservice.domain.exception.InvalidCapabilityException;
import com.adventacademy.meetingservice.domain.exception.MeetingAccessDeniedException;
import com.adventacademy.meetingservice.domain.exception.RoomNotFoundException;
import com.adventacademy.meetingservice.domain.model.MeetingRole;
import com.adventacademy.meetingservice.domain.model.RoomSnapshot;
import com.adventacademy.meetingservice.infrastructure.config.MeetingProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MeetingAccessServiceTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-12T10:00:00Z"), ZoneOffset.UTC);

    private MeetingAccessService accessService;
    private MeetingCapabilityService capabilityService;
    private RoomService roomService;

    @BeforeEach
    void setUp() {
        final MeetingProperties properties = new MeetingProperties();
        capabilityService = new MeetingCapabilityService(CLOCK, properties);
        roomService = new RoomService(CLOCK);
        accessService = new MeetingAccessService(
                roomService,
                capabilityService,
                new MeetingRateLimiter(CLOCK, properties));
    }

    @Test
    void hostControlsPrivateMembershipAndRoles() {
        final UUID hostId = UUID.randomUUID();
        final UUID memberId = UUID.randomUUID();
        final UUID strangerId = UUID.randomUUID();
        final RoomSnapshot room = accessService.createPrivateMeeting(hostId);

        assertThat(accessService.requireRole(room.roomId().value(), hostId)).isEqualTo(MeetingRole.HOST);
        assertThatThrownBy(() -> accessService.issueCapability(room.roomId().value(), strangerId))
                .isInstanceOf(MeetingAccessDeniedException.class);

        assertThat(accessService.addMember(room.roomId().value(), hostId, memberId))
                .isEqualTo(MeetingRole.PARTICIPANT);
        assertThat(accessService.issueCapability(room.roomId().value(), memberId).role())
                .isEqualTo(MeetingRole.PARTICIPANT);
        assertThatThrownBy(() -> accessService.addMember(room.roomId().value(), memberId, strangerId))
                .isInstanceOf(MeetingAccessDeniedException.class);
    }

    @Test
    void removingMemberRevokesOutstandingCapability() {
        final UUID hostId = UUID.randomUUID();
        final UUID memberId = UUID.randomUUID();
        final RoomSnapshot room = accessService.createPrivateMeeting(hostId);
        accessService.addMember(room.roomId().value(), hostId, memberId);
        final IssuedCapability issued = accessService.issueCapability(room.roomId().value(), memberId);

        accessService.removeMember(room.roomId().value(), hostId, memberId);

        assertThatThrownBy(() -> capabilityService.consume(issued.token(), room.roomId().value()))
                .isInstanceOf(InvalidCapabilityException.class);
        assertThatThrownBy(() -> accessService.issueCapability(room.roomId().value(), memberId))
                .isInstanceOf(MeetingAccessDeniedException.class);
    }

    @Test
    void closingRoomRemovesPolicyAndRevokesCapabilities() {
        final UUID hostId = UUID.randomUUID();
        final UUID memberId = UUID.randomUUID();
        final RoomSnapshot room = accessService.createPrivateMeeting(hostId);
        accessService.addMember(room.roomId().value(), hostId, memberId);
        final IssuedCapability issued = accessService.issueCapability(room.roomId().value(), memberId);
        roomService.join(room.roomId().value(), "participant-host");

        roomService.leave(room.roomId().value(), "participant-host");

        assertThat(accessService.policyCount()).isZero();
        assertThat(capabilityService.activeCapabilityCount()).isZero();
        assertThatThrownBy(() -> accessService.addMember(room.roomId().value(), hostId, UUID.randomUUID()))
                .isInstanceOf(RoomNotFoundException.class);
        assertThatThrownBy(() -> capabilityService.consume(issued.token(), room.roomId().value()))
                .isInstanceOf(InvalidCapabilityException.class);
    }
}
