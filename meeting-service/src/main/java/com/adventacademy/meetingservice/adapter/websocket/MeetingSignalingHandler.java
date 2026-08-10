package com.adventacademy.meetingservice.adapter.websocket;

import com.adventacademy.meetingservice.application.service.RoomService;
import com.adventacademy.meetingservice.domain.exception.DuplicateParticipantException;
import com.adventacademy.meetingservice.domain.exception.InvalidSignalException;
import com.adventacademy.meetingservice.domain.exception.MeetingException;
import com.adventacademy.meetingservice.domain.exception.ParticipantNotRegisteredException;
import com.adventacademy.meetingservice.domain.exception.RoomFullException;
import com.adventacademy.meetingservice.domain.exception.RoomNotFoundException;
import com.adventacademy.meetingservice.domain.exception.TargetParticipantNotFoundException;
import com.adventacademy.meetingservice.domain.model.ParticipantId;
import com.adventacademy.meetingservice.domain.model.RoomJoinResult;
import com.adventacademy.meetingservice.domain.model.RoomSnapshot;
import com.adventacademy.meetingservice.infrastructure.config.MeetingProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class MeetingSignalingHandler extends TextWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(MeetingSignalingHandler.class);

    private final RoomService roomService;
    private final SignalMessageValidator validator;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final int maxMessageBytes;
    private final ConcurrentMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, SessionBinding> bindingsBySession = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> sessionsByParticipant = new ConcurrentHashMap<>();

    public MeetingSignalingHandler(
            RoomService roomService,
            SignalMessageValidator validator,
            ObjectMapper objectMapper,
            Clock clock,
            MeetingProperties properties) {
        this.roomService = roomService;
        this.validator = validator;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.maxMessageBytes = properties.getMaxMessageBytes();
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.put(session.getId(), session);
        logger.info("Meeting WebSocket connected sessionId={}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage textMessage) {
        synchronized (session) {
            try {
                final SignalMessage message = parseAndValidate(textMessage);
                routeMessage(session, message);
            } catch (MeetingException | IllegalArgumentException exception) {
                sendFailure(session, exception);
            } catch (JsonProcessingException exception) {
                sendError(session, "INVALID_MESSAGE", "Malformed JSON signaling message");
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        synchronized (session) {
            sessions.remove(session.getId(), session);
            cleanupBinding(session.getId());
        }
        logger.info("Meeting WebSocket disconnected sessionId={} closeCode={}",
                session.getId(), status.getCode());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        logger.warn("Meeting WebSocket transport error sessionId={} errorType={}",
                session.getId(), exception.getClass().getSimpleName());
        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    private SignalMessage parseAndValidate(TextMessage textMessage) throws JsonProcessingException {
        if (textMessage.getPayloadLength() > maxMessageBytes) {
            throw new InvalidSignalException("MESSAGE_TOO_LARGE", "Signaling message exceeds the configured limit");
        }
        final JsonNode tree = objectMapper.readTree(textMessage.getPayload());
        JsonNode typeNode = null;
        if (tree != null) {
            typeNode = tree.get("type");
        }
        if (typeNode == null || !typeNode.isTextual()) {
            throw new InvalidSignalException("INVALID_MESSAGE", "Signal type is required");
        }

        final SignalType type = parseSignalType(typeNode.textValue());
        final SignalMessage message = new SignalMessage(
                type,
                textValue(tree, "roomId"),
                textValue(tree, "senderId"),
                textValue(tree, "targetId"),
                tree.get("payload"),
                clock.instant());
        validator.validate(message);
        return message;
    }

    private void routeMessage(WebSocketSession session, SignalMessage message) {
        switch (message.type()) {
            case JOIN -> joinRoom(session, message);
            case OFFER, ANSWER, ICE_CANDIDATE -> forwardSignal(session, message);
            case LEAVE -> leaveRoom(session, message);
            case PING -> pong(session, message);
            default -> throw new InvalidSignalException(
                    "UNSUPPORTED_SIGNAL_TYPE",
                    "Clients cannot send signal type: " + message.type());
        }
    }

    private void joinRoom(WebSocketSession session, SignalMessage message) {
        if (bindingsBySession.containsKey(session.getId())) {
            throw new DuplicateParticipantException(message.senderId());
        }
        final String existingSession = sessionsByParticipant.putIfAbsent(message.senderId(), session.getId());
        if (existingSession != null) {
            throw new DuplicateParticipantException(message.senderId());
        }

        try {
            final RoomJoinResult result = roomService.join(message.roomId(), message.senderId());
            final SessionBinding binding = new SessionBinding(session.getId(), message.senderId(), message.roomId());
            bindingsBySession.put(session.getId(), binding);
            sendJoined(session, binding, result);
            result.peerId().ifPresent(peerId -> notifyParticipantJoined(binding, peerId));
            logger.info("Participant joined meeting roomId={} participantId={} participantCount={}",
                    message.roomId(), message.senderId(), result.room().participants().size());
        } catch (RuntimeException exception) {
            sessionsByParticipant.remove(message.senderId(), session.getId());
            throw exception;
        }
    }

    private void forwardSignal(WebSocketSession senderSession, SignalMessage message) {
        final SessionBinding sender = requireBinding(senderSession.getId());
        verifySender(message, sender);
        final SessionBinding target = requireTarget(message.targetId());
        if (!sender.roomId().equals(message.roomId()) || !sender.roomId().equals(target.roomId())) {
            throw new InvalidSignalException("CROSS_ROOM_SIGNALING", "Sender and target must belong to the same room");
        }

        final WebSocketSession targetSession = requireOpenSession(target.sessionId(), target.participantId());
        final SignalMessage outgoing = signal(
                message.type(), sender.roomId(), sender.participantId(), target.participantId(), message.payload());
        if (!safeSend(targetSession, outgoing)) {
            cleanupBinding(target.sessionId());
            throw new TargetParticipantNotFoundException(target.participantId());
        }
        logger.info("Forwarded meeting signal type={} roomId={} senderId={} targetId={}",
                message.type(), sender.roomId(), sender.participantId(), target.participantId());
    }

    private void leaveRoom(WebSocketSession session, SignalMessage message) {
        final SessionBinding binding = requireBinding(session.getId());
        verifySender(message, binding);
        cleanupBinding(session.getId());
    }

    private void pong(WebSocketSession session, SignalMessage message) {
        final SessionBinding binding = requireBinding(session.getId());
        verifySender(message, binding);
        safeSend(session, signal(SignalType.PONG, binding.roomId(), null, binding.participantId(), null));
    }

    private void sendJoined(WebSocketSession session, SessionBinding binding, RoomJoinResult result) {
        final ObjectNode payload = objectMapper.createObjectNode();
        payload.put("participantId", binding.participantId());
        result.peerId().ifPresentOrElse(
                peerId -> payload.put("peerId", peerId.value()),
                () -> payload.putNull("peerId"));
        payload.put("initiator", false);
        payload.put("status", result.room().status().name());
        safeSend(session, signal(SignalType.JOINED, binding.roomId(), null, binding.participantId(), payload));
    }

    private void notifyParticipantJoined(SessionBinding joining, ParticipantId peerId) {
        final SessionBinding peer = requireTarget(peerId.value());
        final WebSocketSession peerSession = requireOpenSession(peer.sessionId(), peer.participantId());
        final ObjectNode payload = objectMapper.createObjectNode();
        payload.put("participantId", joining.participantId());
        payload.put("initiator", true);
        safeSend(peerSession, signal(
                SignalType.PARTICIPANT_JOINED,
                joining.roomId(),
                joining.participantId(),
                peer.participantId(),
                payload));
    }

    private void cleanupBinding(String sessionId) {
        final SessionBinding binding = bindingsBySession.remove(sessionId);
        if (binding == null) {
            return;
        }
        sessionsByParticipant.remove(binding.participantId(), sessionId);
        try {
            final RoomSnapshot room = roomService.leave(binding.roomId(), binding.participantId());
            notifyParticipantLeft(binding, room);
        } catch (RoomNotFoundException exception) {
            logger.debug("Room already removed during disconnect cleanup roomId={}", binding.roomId());
        }
    }

    private void notifyParticipantLeft(SessionBinding leaving, RoomSnapshot room) {
        final ObjectNode payload = objectMapper.createObjectNode();
        payload.put("participantId", leaving.participantId());
        for (ParticipantId participantId : room.participants()) {
            final String targetSessionId = sessionsByParticipant.get(participantId.value());
            if (targetSessionId != null) {
                final WebSocketSession targetSession = sessions.get(targetSessionId);
                if (targetSession != null) {
                    safeSend(targetSession, signal(
                            SignalType.PARTICIPANT_LEFT,
                            leaving.roomId(),
                            leaving.participantId(),
                            participantId.value(),
                            payload));
                }
            }
        }
        logger.info("Participant left meeting roomId={} participantId={} participantCount={}",
                leaving.roomId(), leaving.participantId(), room.participants().size());
    }

    private SessionBinding requireBinding(String sessionId) {
        final SessionBinding binding = bindingsBySession.get(sessionId);
        if (binding == null) {
            throw new ParticipantNotRegisteredException(sessionId);
        }
        return binding;
    }

    private SessionBinding requireTarget(String participantId) {
        final String targetSessionId = sessionsByParticipant.get(participantId);
        if (targetSessionId == null) {
            throw new TargetParticipantNotFoundException(participantId);
        }
        final SessionBinding binding = bindingsBySession.get(targetSessionId);
        if (binding == null) {
            throw new TargetParticipantNotFoundException(participantId);
        }
        return binding;
    }

    private WebSocketSession requireOpenSession(String sessionId, String participantId) {
        final WebSocketSession session = sessions.get(sessionId);
        if (session == null || !session.isOpen()) {
            throw new TargetParticipantNotFoundException(participantId);
        }
        return session;
    }

    private void verifySender(SignalMessage message, SessionBinding binding) {
        if (!binding.roomId().equals(message.roomId())) {
            throw new InvalidSignalException("CROSS_ROOM_SIGNALING", "The session is not bound to the requested room");
        }
        if (message.senderId() != null && !binding.participantId().equals(message.senderId())) {
            throw new InvalidSignalException("SENDER_MISMATCH", "senderId does not match the WebSocket session");
        }
    }

    private void sendFailure(WebSocketSession session, RuntimeException exception) {
        String code = "INVALID_MESSAGE";
        if (exception instanceof MeetingException meetingException) {
            code = meetingException.getCode();
        }
        if (exception instanceof RoomFullException) {
            sendErrorOfType(session, SignalType.ROOM_FULL, code, exception.getMessage());
        } else {
            sendError(session, code, exception.getMessage());
        }
    }

    private void sendError(WebSocketSession session, String code, String message) {
        sendErrorOfType(session, SignalType.ERROR, code, message);
    }

    private void sendErrorOfType(WebSocketSession session, SignalType type, String code, String message) {
        final ObjectNode payload = objectMapper.createObjectNode();
        payload.put("code", code);
        payload.put("message", message);
        safeSend(session, signal(type, null, null, null, payload));
    }

    private SignalMessage signal(
            SignalType type,
            String roomId,
            String senderId,
            String targetId,
            JsonNode payload) {
        return new SignalMessage(type, roomId, senderId, targetId, payload, clock.instant());
    }

    private boolean safeSend(WebSocketSession session, SignalMessage message) {
        if (!session.isOpen()) {
            return false;
        }
        try {
            synchronized (session) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(message)));
            }
            return true;
        } catch (IOException exception) {
            logger.warn("Unable to send meeting signal sessionId={} type={}", session.getId(), message.type());
            return false;
        }
    }

    private SignalType parseSignalType(String value) {
        try {
            return SignalType.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new InvalidSignalException("UNSUPPORTED_SIGNAL_TYPE", "Unsupported signal type: " + value);
        }
    }

    private String textValue(JsonNode tree, String field) {
        final JsonNode value = tree.get(field);
        if (value != null && value.isTextual()) {
            return value.textValue();
        }
        return null;
    }
}
