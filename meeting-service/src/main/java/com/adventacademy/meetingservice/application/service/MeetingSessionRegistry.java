package com.adventacademy.meetingservice.application.service;

import com.adventacademy.meetingservice.application.session.MeetingSession;
import com.adventacademy.meetingservice.application.session.SessionDeparture;
import com.adventacademy.meetingservice.application.session.SessionRegistration;
import com.adventacademy.meetingservice.application.session.SessionSignalType;
import com.adventacademy.meetingservice.application.session.SignalRoute;
import com.adventacademy.meetingservice.application.security.MeetingGrant;
import com.adventacademy.meetingservice.domain.exception.InvalidSignalException;
import com.adventacademy.meetingservice.domain.exception.ParticipantNotRegisteredException;
import com.adventacademy.meetingservice.domain.exception.TargetParticipantNotFoundException;
import com.adventacademy.meetingservice.domain.model.ParticipantId;
import com.adventacademy.meetingservice.domain.model.RoomJoinResult;
import com.adventacademy.meetingservice.domain.model.RoomSnapshot;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class MeetingSessionRegistry {

    private final RoomService roomService;
    private final Map<String, MeetingSession> sessionsByConnection = new HashMap<>();
    private final Map<String, MeetingSession> sessionsByParticipant = new HashMap<>();

    public MeetingSessionRegistry(RoomService roomService) {
        this.roomService = roomService;
    }

    public synchronized SessionRegistration join(String sessionId, MeetingGrant grant) {
        final MeetingSession existing = sessionsByConnection.get(sessionId);
        if (existing != null) {
            if (!existing.roomId().equals(grant.roomId()) || !existing.userId().equals(grant.userId())) {
                throw new InvalidSignalException(
                        "SESSION_ALREADY_BOUND",
                        "The WebSocket session is already bound to another room");
            }
            return currentRegistration(existing, false);
        }

        final ParticipantId participantId = participantId(grant);
        final RoomJoinResult joined = roomService.join(grant.roomId(), participantId.value());
        final MeetingSession session = new MeetingSession(
                sessionId,
                participantId.value(),
                grant.roomId(),
                grant.userId(),
                grant.role());
        sessionsByConnection.put(sessionId, session);
        sessionsByParticipant.put(participantId.value(), session);
        final Optional<MeetingSession> peer = joined.peerId().map(this::requireRegisteredParticipant);
        return new SessionRegistration(session, joined.room(), peer, true);
    }

    public synchronized SessionRegistration current(String sessionId) {
        return currentRegistration(requireSession(sessionId), false);
    }

    public synchronized Optional<MeetingSession> findBySession(String sessionId) {
        return Optional.ofNullable(sessionsByConnection.get(sessionId));
    }

    public synchronized Optional<MeetingSession> findByParticipant(String participantId) {
        return Optional.ofNullable(sessionsByParticipant.get(participantId));
    }

    public synchronized Optional<MeetingSession> findByRoomAndUser(String roomId, java.util.UUID userId) {
        return sessionsByConnection.values().stream()
                .filter(session -> session.roomId().equals(roomId) && session.userId().equals(userId))
                .findFirst();
    }

    public synchronized Optional<SessionDeparture> leave(String sessionId) {
        final MeetingSession session = sessionsByConnection.remove(sessionId);
        if (session == null) {
            return Optional.empty();
        }
        sessionsByParticipant.remove(session.participantId(), session);
        final RoomSnapshot room = roomService.leave(session.roomId(), session.participantId());
        return Optional.of(new SessionDeparture(session, room));
    }

    public synchronized SignalRoute requireRoute(
            String sessionId,
            String requestedRoomId,
            String targetParticipantId,
            SessionSignalType type) {
        final MeetingSession sender = requireSession(sessionId);
        if (!sender.roomId().equals(requestedRoomId)) {
            throw new InvalidSignalException(
                    "CROSS_ROOM_SIGNALING",
                    "The WebSocket session is not bound to the requested room");
        }

        final MeetingSession target = sessionsByParticipant.get(targetParticipantId);
        if (target == null) {
            throw new TargetParticipantNotFoundException();
        }
        if (!sender.roomId().equals(target.roomId())) {
            throw new InvalidSignalException(
                    "CROSS_ROOM_SIGNALING",
                    "Sender and target must belong to the same room");
        }

        enforceDeterministicOfferer(sender, target, type);
        return new SignalRoute(sender, target);
    }

    public synchronized MeetingSession requireSender(String sessionId, String requestedRoomId) {
        final MeetingSession sender = requireSession(sessionId);
        if (!sender.roomId().equals(requestedRoomId)) {
            throw new InvalidSignalException(
                    "CROSS_ROOM_SIGNALING",
                    "The WebSocket session is not bound to the requested room");
        }
        return sender;
    }

    public synchronized int sessionCount() {
        return sessionsByConnection.size();
    }

    private SessionRegistration currentRegistration(MeetingSession session, boolean newlyJoined) {
        final RoomSnapshot room = roomService.getRoom(session.roomId());
        final Optional<MeetingSession> peer = room.participants().stream()
                .filter(participantId -> !participantId.value().equals(session.participantId()))
                .findFirst()
                .map(this::requireRegisteredParticipant);
        return new SessionRegistration(session, room, peer, newlyJoined);
    }

    private void enforceDeterministicOfferer(
            MeetingSession sender,
            MeetingSession target,
            SessionSignalType type) {
        if (type != SessionSignalType.OFFER && type != SessionSignalType.ANSWER) {
            return;
        }
        final RoomSnapshot room = roomService.getRoom(sender.roomId());
        final java.util.List<String> order = room.participants().stream().map(ParticipantId::value).toList();
        final int senderIndex = order.indexOf(sender.participantId());
        final int targetIndex = order.indexOf(target.participantId());
        boolean valid = senderIndex > targetIndex;
        if (type == SessionSignalType.OFFER) {
            valid = senderIndex < targetIndex;
        }
        if (!valid) {
            throw new InvalidSignalException(
                    "INVALID_NEGOTIATION_ROLE",
                    "Only the server-selected participant may create an offer");
        }
    }

    private MeetingSession requireSession(String sessionId) {
        final MeetingSession session = sessionsByConnection.get(sessionId);
        if (session == null) {
            throw new ParticipantNotRegisteredException();
        }
        return session;
    }

    private MeetingSession requireRegisteredParticipant(ParticipantId participantId) {
        final MeetingSession session = sessionsByParticipant.get(participantId.value());
        if (session == null) {
            throw new IllegalStateException("Room membership and meeting session registry are inconsistent");
        }
        return session;
    }

    private ParticipantId participantId(MeetingGrant grant) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(grant.roomId().getBytes(StandardCharsets.UTF_8));
            digest.update(ByteBuffer.allocate(Long.BYTES * 2)
                    .putLong(grant.userId().getMostSignificantBits())
                    .putLong(grant.userId().getLeastSignificantBits())
                    .array());
            final String encoded = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(digest.digest()).substring(0, 22);
            return new ParticipantId("participant-" + encoded);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
