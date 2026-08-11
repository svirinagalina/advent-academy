package com.adventacademy.meetingservice.application.service;

import com.adventacademy.meetingservice.application.session.MeetingSession;
import com.adventacademy.meetingservice.application.session.SessionDeparture;
import com.adventacademy.meetingservice.application.session.SessionRegistration;
import com.adventacademy.meetingservice.application.session.SessionSignalType;
import com.adventacademy.meetingservice.application.session.SignalRoute;
import com.adventacademy.meetingservice.domain.exception.InvalidSignalException;
import com.adventacademy.meetingservice.domain.exception.ParticipantNotRegisteredException;
import com.adventacademy.meetingservice.domain.exception.TargetParticipantNotFoundException;
import com.adventacademy.meetingservice.domain.model.ParticipantId;
import com.adventacademy.meetingservice.domain.model.RoomJoinResult;
import com.adventacademy.meetingservice.domain.model.RoomSnapshot;
import org.springframework.stereotype.Service;

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

    public synchronized SessionRegistration join(String sessionId, String roomId) {
        final MeetingSession existing = sessionsByConnection.get(sessionId);
        if (existing != null) {
            if (!existing.roomId().equals(roomId)) {
                throw new InvalidSignalException(
                        "SESSION_ALREADY_BOUND",
                        "The WebSocket session is already bound to another room");
            }
            return currentRegistration(existing, false);
        }

        final ParticipantId participantId = generateParticipantId();
        final RoomJoinResult joined = roomService.join(roomId, participantId.value());
        final MeetingSession session = new MeetingSession(sessionId, participantId.value(), roomId);
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
        if (room.participants().size() != 2) {
            throw new InvalidSignalException(
                    "INVALID_NEGOTIATION_STATE",
                    "Offer and answer require two active participants");
        }
        final String offererId = room.participants().getFirst().value();
        final String answererId = room.participants().getLast().value();
        boolean valid = sender.participantId().equals(answererId)
                && target.participantId().equals(offererId);
        if (type == SessionSignalType.OFFER) {
            valid = sender.participantId().equals(offererId)
                    && target.participantId().equals(answererId);
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

    private ParticipantId generateParticipantId() {
        ParticipantId participantId;
        do {
            participantId = ParticipantId.generate();
        } while (sessionsByParticipant.containsKey(participantId.value()));
        return participantId;
    }
}
