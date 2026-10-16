package com.adventacademy.meetingservice.adapter.websocket;

import com.adventacademy.meetingservice.application.service.MeetingSessionRegistry;
import com.adventacademy.meetingservice.application.security.MeetingCapabilityService;
import com.adventacademy.meetingservice.application.security.MeetingGrant;
import com.adventacademy.meetingservice.application.security.MeetingRateLimiter;
import com.adventacademy.meetingservice.application.session.MeetingSession;
import com.adventacademy.meetingservice.application.session.SessionDeparture;
import com.adventacademy.meetingservice.application.session.SessionRegistration;
import com.adventacademy.meetingservice.application.session.SessionSignalType;
import com.adventacademy.meetingservice.application.session.SignalRoute;
import com.adventacademy.meetingservice.domain.exception.InvalidSignalException;
import com.adventacademy.meetingservice.domain.exception.MeetingException;
import com.adventacademy.meetingservice.domain.exception.RoomFullException;
import com.adventacademy.meetingservice.domain.exception.TargetParticipantNotFoundException;
import com.adventacademy.meetingservice.domain.model.ParticipantId;
import com.adventacademy.meetingservice.infrastructure.config.MeetingProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import com.adventacademy.meetingservice.application.service.ScheduledMeetingService;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.UUID;

@Component
public class MeetingSignalingHandler extends TextWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(MeetingSignalingHandler.class);

    private final MeetingSessionRegistry sessionRegistry;
    private final SignalMessageValidator validator;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final int maxMessageBytes;
    private final MeetingCapabilityService capabilityService;
    private final MeetingRateLimiter rateLimiter;
    private final ScheduledMeetingService scheduled;
    private final ConcurrentMap<String, MeetingGrant> grants = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public MeetingSignalingHandler(
            MeetingSessionRegistry sessionRegistry,
            SignalMessageValidator validator,
            ObjectMapper objectMapper,
            Clock clock,
            MeetingProperties properties,
            MeetingCapabilityService capabilityService,
            MeetingRateLimiter rateLimiter) {
        this(sessionRegistry, validator, objectMapper, clock, properties, capabilityService, rateLimiter, null);
    }

    private com.adventacademy.security.AccessTokenStatusClient accessStatus;

    @Autowired
    public void accessStatus(com.adventacademy.security.AccessTokenStatusClient client) { this.accessStatus = client; }

    @Autowired
    public MeetingSignalingHandler(MeetingSessionRegistry sessionRegistry, SignalMessageValidator validator,
            ObjectMapper objectMapper, Clock clock, MeetingProperties properties,
            MeetingCapabilityService capabilityService, MeetingRateLimiter rateLimiter, ScheduledMeetingService scheduled) {
        this.scheduled = scheduled;
        this.sessionRegistry = sessionRegistry;
        this.validator = validator;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.maxMessageBytes = properties.getMaxMessageBytes();
        this.capabilityService = capabilityService;
        this.rateLimiter = rateLimiter;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.put(session.getId(), session);
        logger.info("Meeting WebSocket connected");
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage textMessage) {
        try {
            final SignalMessage message = parseAndValidate(textMessage);
            routeMessage(session, message);
        } catch (com.adventacademy.security.AccessTokenStatusClient.InactiveTokenException
                 | com.adventacademy.security.AccessTokenStatusClient.IdentityUnavailableException exception) {
            final MeetingGrant grant = grants.get(session.getId());
            if (grant != null) { revoke(grant.roomId(), grant.userId()); }
            else { sendError(session, "ACCESS_REVOKED", "Meeting authorization is no longer valid"); }
        } catch (MeetingException | IllegalArgumentException exception) {
            sendFailure(session, exception);
        } catch (JsonProcessingException exception) {
            sendError(session, "INVALID_MESSAGE", "Malformed JSON signaling message");
        } catch (RuntimeException exception) {
            logger.error("Unexpected meeting signaling failure errorType={}",
                    exception.getClass().getSimpleName());
            sendError(session, "INTERNAL_SIGNALING_ERROR", "Unable to process signaling message");
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId(), session);
        cleanupBinding(session.getId(), true);
        logger.info("Meeting WebSocket disconnected closeCode={}", status.getCode());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        logger.warn("Meeting WebSocket transport error errorType={}",
                exception.getClass().getSimpleName());
        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    public void revoke(String roomId, UUID userId) {
        sessionRegistry.findByRoomAndUser(roomId, userId).ifPresent(binding -> {
            final WebSocketSession session = sessions.get(binding.sessionId());
            if (session == null) {
                cleanupBinding(binding.sessionId(), true);
                return;
            }
            sendError(session, "ACCESS_REVOKED", "Meeting access was revoked by the host");
            sessions.remove(binding.sessionId(), session);
            cleanupBinding(binding.sessionId(), true);
            try {
                synchronized (session) {
                    if (session.isOpen()) {
                        session.close(CloseStatus.POLICY_VIOLATION);
                    }
                }
            } catch (IOException exception) {
                logger.warn("Unable to close revoked meeting WebSocket");
            }
        });
    }

    public void revokeRoom(String roomId) {
        capabilityService.revokeRoom(roomId);
        for (MeetingGrant grant : grants.values()) {
            if (grant.roomId().equals(roomId)) {
                revoke(roomId, grant.userId());
            }
        }
    }

    private void requireActive(MeetingGrant grant) {
        if (accessStatus == null) { return; } // Unit-constructed handlers have no identity transport.
        if (grant.authorization() == null || !grant.authorization().startsWith("Bearer ")) {
            throw new com.adventacademy.security.AccessTokenStatusClient.InactiveTokenException();
        }
        accessStatus.requireActive(grant.authorization().substring(7));
    }

    private void renew(WebSocketSession session, SignalMessage message) {
        final MeetingSession binding = sessionRegistry.requireSender(session.getId(), message.roomId());
        final MeetingGrant grant = capabilityService.consume(message.capability(), message.roomId());
        if (!binding.userId().equals(grant.userId()) || binding.role() != grant.role()) {
            throw new InvalidSignalException("INVALID_CAPABILITY", "Renewal must retain the meeting identity");
        }
        requireActive(grant);
        if (grant.scheduleVersion() != null) { scheduled.requireOpen(grant); }
        grants.put(session.getId(), grant);
    }

    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 15000)
    public void revokeInactiveSessions() {
        grants.forEach((session, grant) -> {
            try {
                requireActive(grant);
                if (grant.scheduleVersion() != null) { scheduled.requireOpen(grant); }
            }
            catch (com.adventacademy.security.AccessTokenStatusClient.InactiveTokenException
                   | com.adventacademy.security.AccessTokenStatusClient.IdentityUnavailableException
                   | MeetingException exception) {
                revoke(grant.roomId(), grant.userId());
            }
        });
    }

    private SignalMessage parseAndValidate(TextMessage textMessage) throws JsonProcessingException {
        if (textMessage.getPayload().getBytes(StandardCharsets.UTF_8).length > maxMessageBytes) {
            throw new InvalidSignalException(
                    "MESSAGE_TOO_LARGE",
                    "Signaling message exceeds the configured limit");
        }
        final JsonNode tree = objectMapper.readTree(textMessage.getPayload());
        JsonNode typeNode = null;
        if (tree != null) {
            typeNode = tree.get("type");
        }
        if (typeNode == null || !typeNode.isTextual()) {
            throw new InvalidSignalException("INVALID_MESSAGE", "Signal type is required");
        }

        final SignalMessage message = new SignalMessage(
                intValue(tree, "version"),
                parseSignalType(typeNode.textValue()),
                textValue(tree, "roomId"),
                textValue(tree, "capability"),
                null,
                textValue(tree, "targetId"),
                tree.get("payload"),
                clock.instant());
        validator.validate(tree, message);
        return message;
    }

    private void routeMessage(WebSocketSession session, SignalMessage message) {
        final MeetingGrant current = grants.get(session.getId());
        if (current != null && message.type() != SignalType.JOIN && message.type() != SignalType.LEAVE
                && message.type() != SignalType.REAUTHORIZE) {
            requireActive(current);
            if (current.scheduleVersion() != null) { scheduled.requireOpen(current); }
        }
        switch (message.type()) {
            case JOIN -> joinRoom(session, message);
            case REAUTHORIZE -> renew(session, message);
            case OFFER, ANSWER, ICE_CANDIDATE -> forwardSignal(session, message);
            case LEAVE -> leaveRoom(session, message);
            case PING -> pong(session, message);
            default -> throw new InvalidSignalException(
                    "UNSUPPORTED_SIGNAL_TYPE",
                    "Clients cannot send signal type: " + message.type());
        }
    }

    private void joinRoom(WebSocketSession session, SignalMessage message) {
        final Optional<MeetingSession> existing = sessionRegistry.findBySession(session.getId());
        final MeetingGrant grant;
        if (existing.isPresent()) {
            final MeetingSession binding = existing.orElseThrow();
            grant = grants.getOrDefault(session.getId(),
                    new MeetingGrant(binding.roomId(), binding.userId(), binding.role()));
        } else {
            rateLimiter.requireJoinAllowed(remoteKey(session));
            grant = capabilityService.consume(message.capability(), message.roomId());
        }
        requireActive(grant);
        SessionRegistration registration;
        if (grant.scheduleVersion() != null) {
            registration = scheduled.join(grant, () -> {
                final SessionRegistration joined = sessionRegistry.join(session.getId(), grant);
                grants.put(session.getId(), grant);
                return joined;
            });
        } else {
            registration = sessionRegistry.join(session.getId(), grant);
        }
        grants.put(session.getId(), grant);
        registration = removeClosedPeer(registration);
        if (!sendJoined(session, registration)) {
            cleanupBinding(session.getId(), false);
            return;
        }
        if (registration.newlyJoined()) {
            for (ParticipantId id : registration.room().participants()) {
                if (!id.value().equals(registration.session().participantId())) {
                    final MeetingSession peer = sessionRegistry.findByParticipant(id.value()).orElse(null);
                    if (peer != null && !notifyParticipantJoined(registration.session(), peer)) {
                        cleanupBinding(peer.sessionId(), true);
                    }
                }
            }
        }
        logger.info("Participant joined meeting participantCount={} newlyJoined={}",
                registration.room().participants().size(), registration.newlyJoined());
    }

    private SessionRegistration removeClosedPeer(SessionRegistration registration) {
        for (ParticipantId id : registration.room().participants()) {
            if (!id.value().equals(registration.session().participantId())) {
                final Optional<MeetingSession> peer = sessionRegistry.findByParticipant(id.value());
                if (peer.isPresent() && openSession(peer.orElseThrow()).isEmpty()) {
                    cleanupBinding(peer.orElseThrow().sessionId(), false);
                }
            }
        }
        final SessionRegistration current = sessionRegistry.current(registration.session().sessionId());
        return new SessionRegistration(current.session(), current.room(), current.peer(), registration.newlyJoined());
    }

    private void forwardSignal(WebSocketSession senderSession, SignalMessage message) {
        final SignalRoute route = sessionRegistry.requireRoute(
                senderSession.getId(),
                message.roomId(),
                message.targetId(),
                SessionSignalType.valueOf(message.type().name()));
        final WebSocketSession targetSession = openSession(route.target())
                .orElseThrow(TargetParticipantNotFoundException::new);
        final SignalMessage outgoing = signal(
                message.type(),
                route.sender().roomId(),
                route.sender().participantId(),
                route.target().participantId(),
                message.payload());
        if (!safeSend(targetSession, outgoing)) {
            cleanupBinding(route.target().sessionId(), true);
            throw new TargetParticipantNotFoundException();
        }
        logger.info("Forwarded meeting signal type={}", message.type());
    }

    private void leaveRoom(WebSocketSession session, SignalMessage message) {
        final Optional<MeetingSession> binding = sessionRegistry.findBySession(session.getId());
        boolean released = false;
        if (binding.isPresent()) {
            sessionRegistry.requireSender(session.getId(), message.roomId());
            cleanupBinding(session.getId(), true);
            released = true;
        }
        final ObjectNode payload = objectMapper.createObjectNode();
        payload.put("released", released);
        safeSend(session, signal(SignalType.LEFT, message.roomId(), null, null, payload));
    }

    private void pong(WebSocketSession session, SignalMessage message) {
        final MeetingSession sender = sessionRegistry.requireSender(session.getId(), message.roomId());
        safeSend(session, signal(SignalType.PONG, sender.roomId(), null, sender.participantId(), null));
    }

    private boolean sendJoined(WebSocketSession session, SessionRegistration registration) {
        final ObjectNode payload = objectMapper.createObjectNode();
        payload.put("participantId", registration.session().participantId());
        registration.peer().ifPresentOrElse(
                peer -> payload.put("peerId", peer.participantId()),
                () -> payload.putNull("peerId"));
        final boolean initiator = !registration.room().participants().isEmpty()
                && registration.room().participants().getFirst().value()
                .equals(registration.session().participantId());
        final var peers = payload.putArray("peers");
        final var order = registration.room().participants();
        final int ownIndex = order.indexOf(new ParticipantId(registration.session().participantId()));
        for (int index = 0; index < order.size(); index++) {
            if (index != ownIndex) {
                peers.addObject().put("participantId", order.get(index).value()).put("initiator", ownIndex < index);
            }
        }
        payload.put("initiator", initiator);
        payload.put("status", registration.room().status().name());
        payload.put("role", registration.session().role().name());
        return safeSend(session, signal(
                SignalType.JOINED,
                registration.session().roomId(),
                null,
                registration.session().participantId(),
                payload));
    }

    private boolean notifyParticipantJoined(MeetingSession joining, MeetingSession peer) {
        final Optional<WebSocketSession> peerSession = openSession(peer);
        if (peerSession.isEmpty()) {
            return false;
        }
        final ObjectNode payload = objectMapper.createObjectNode();
        payload.put("participantId", joining.participantId());
        final var order = sessionRegistry.current(peer.sessionId()).room().participants();
        payload.put("initiator", order.indexOf(new ParticipantId(peer.participantId()))
                < order.indexOf(new ParticipantId(joining.participantId())));
        return safeSend(peerSession.orElseThrow(), signal(
                SignalType.PARTICIPANT_JOINED,
                joining.roomId(),
                joining.participantId(),
                peer.participantId(),
                payload));
    }

    private void cleanupBinding(String sessionId, boolean notifyPeers) {
        grants.remove(sessionId);
        final Optional<SessionDeparture> departure = sessionRegistry.leave(sessionId);
        if (departure.isEmpty()) {
            return;
        }
        if (notifyPeers) {
            notifyParticipantLeft(departure.orElseThrow());
        }
    }

    private void notifyParticipantLeft(SessionDeparture departure) {
        final ObjectNode payload = objectMapper.createObjectNode();
        payload.put("participantId", departure.session().participantId());
        for (ParticipantId participantId : departure.room().participants()) {
            sessionRegistry.findByParticipant(participantId.value())
                    .flatMap(this::openSession)
                    .ifPresent(session -> safeSend(session, signal(
                            SignalType.PARTICIPANT_LEFT,
                            departure.session().roomId(),
                            departure.session().participantId(),
                            participantId.value(),
                            payload)));
        }
        logger.info("Participant left meeting participantCount={}",
                departure.room().participants().size());
    }

    private Optional<WebSocketSession> openSession(MeetingSession meetingSession) {
        final WebSocketSession session = sessions.get(meetingSession.sessionId());
        if (session == null || !session.isOpen()) {
            return Optional.empty();
        }
        return Optional.of(session);
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
        return new SignalMessage(
                SignalMessageValidator.PROTOCOL_VERSION,
                type,
                roomId,
                null,
                senderId,
                targetId,
                payload,
                clock.instant());
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
            logger.warn("Unable to send meeting signal type={}", message.type());
            return false;
        }
    }

    private SignalType parseSignalType(String value) {
        try {
            return SignalType.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new InvalidSignalException(
                    "UNSUPPORTED_SIGNAL_TYPE",
                    "Unsupported signal type: " + value);
        }
    }

    private String textValue(JsonNode tree, String field) {
        JsonNode value = null;
        if (tree != null) {
            value = tree.get(field);
        }
        if (value != null && value.isTextual()) {
            return value.textValue();
        }
        return null;
    }

    private int intValue(JsonNode tree, String field) {
        JsonNode value = null;
        if (tree != null) {
            value = tree.get(field);
        }
        if (value != null && value.isIntegralNumber() && value.canConvertToInt()) {
            return value.intValue();
        }
        return -1;
    }

    private String remoteKey(WebSocketSession session) {
        if (session.getRemoteAddress() == null || session.getRemoteAddress().getAddress() == null) {
            return "unknown";
        }
        return session.getRemoteAddress().getAddress().getHostAddress();
    }
}
