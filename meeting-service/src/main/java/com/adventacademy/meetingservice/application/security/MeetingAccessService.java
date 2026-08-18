package com.adventacademy.meetingservice.application.security;

import com.adventacademy.meetingservice.application.service.RoomClosedListener;
import com.adventacademy.meetingservice.application.service.RoomService;
import com.adventacademy.meetingservice.domain.exception.MeetingAccessDeniedException;
import com.adventacademy.meetingservice.domain.exception.RoomNotFoundException;
import com.adventacademy.meetingservice.domain.model.MeetingRole;
import com.adventacademy.meetingservice.domain.model.RoomId;
import com.adventacademy.meetingservice.domain.model.RoomSnapshot;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class MeetingAccessService implements RoomClosedListener {

    private final RoomService roomService;
    private final MeetingCapabilityService capabilityService;
    private final MeetingRateLimiter rateLimiter;
    private final Map<String, AccessPolicy> policies = new HashMap<>();

    public MeetingAccessService(
            RoomService roomService,
            MeetingCapabilityService capabilityService,
            MeetingRateLimiter rateLimiter) {
        this.roomService = roomService;
        this.capabilityService = capabilityService;
        this.rateLimiter = rateLimiter;
        roomService.registerRoomClosedListener(this);
    }

    public synchronized RoomSnapshot createPrivateMeeting(UUID hostId) {
        rateLimiter.requireRoomCreationAllowed(hostId);
        final RoomSnapshot room = roomService.createRoom();
        policies.put(room.roomId().value(), new AccessPolicy(hostId));
        return room;
    }

    public synchronized RoomSnapshot getAuthorizedRoom(String roomId, UUID userId) {
        requireRole(roomId, userId);
        return roomService.getRoom(roomId);
    }

    public synchronized MeetingRole addMember(String roomId, UUID actorId, UUID memberId) {
        final AccessPolicy policy = requirePolicy(roomId);
        requireHost(policy, actorId);
        policy.members().add(memberId);
        return role(policy, memberId);
    }

    public synchronized void removeMember(String roomId, UUID actorId, UUID memberId) {
        final AccessPolicy policy = requirePolicy(roomId);
        requireHost(policy, actorId);
        if (policy.hostId().equals(memberId)) {
            throw new IllegalArgumentException("The meeting host cannot revoke their own host role");
        }
        policy.members().remove(memberId);
        capabilityService.revoke(roomId, memberId);
    }

    public synchronized IssuedCapability issueCapability(String roomId, UUID userId) {
        rateLimiter.requireCapabilityAllowed(userId);
        roomService.getRoom(roomId);
        return capabilityService.issue(roomId, userId, requireRole(roomId, userId));
    }

    public synchronized MeetingRole requireRole(String roomId, UUID userId) {
        final AccessPolicy policy = requirePolicy(roomId);
        final MeetingRole role = role(policy, userId);
        if (role == null) {
            throw new MeetingAccessDeniedException();
        }
        return role;
    }

    @Override
    public synchronized void roomClosed(String roomId) {
        policies.remove(roomId);
        capabilityService.revokeRoom(roomId);
    }

    synchronized int policyCount() {
        return policies.size();
    }

    private AccessPolicy requirePolicy(String roomId) {
        new RoomId(roomId);
        roomService.getRoom(roomId);
        final AccessPolicy policy = policies.get(roomId);
        if (policy == null) {
            throw new RoomNotFoundException(roomId);
        }
        return policy;
    }

    private void requireHost(AccessPolicy policy, UUID actorId) {
        if (!policy.hostId().equals(actorId)) {
            throw new MeetingAccessDeniedException();
        }
    }

    private MeetingRole role(AccessPolicy policy, UUID userId) {
        if (policy.hostId().equals(userId)) {
            return MeetingRole.HOST;
        }
        if (policy.members().contains(userId)) {
            return MeetingRole.PARTICIPANT;
        }
        return null;
    }

    private record AccessPolicy(UUID hostId, Set<UUID> members) {

        private AccessPolicy(UUID hostId) {
            this(hostId, new HashSet<>());
        }
    }
}
