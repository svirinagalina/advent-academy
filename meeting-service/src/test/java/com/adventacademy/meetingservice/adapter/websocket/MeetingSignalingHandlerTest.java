package com.adventacademy.meetingservice.adapter.websocket;

import com.adventacademy.meetingservice.application.service.MeetingSessionRegistry;
import com.adventacademy.meetingservice.application.service.RoomService;
import com.adventacademy.meetingservice.application.security.MeetingCapabilityService;
import com.adventacademy.meetingservice.application.security.MeetingRateLimiter;
import com.adventacademy.meetingservice.application.session.MeetingSession;
import com.adventacademy.meetingservice.application.session.SignalRoute;
import com.adventacademy.meetingservice.domain.model.MeetingRole;
import com.adventacademy.meetingservice.domain.model.RoomSnapshot;
import com.adventacademy.meetingservice.infrastructure.config.MeetingProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MeetingSignalingHandlerTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-11T10:00:00Z"), ZoneOffset.UTC);

    private ObjectMapper objectMapper;
    private RoomService roomService;
    private MeetingSessionRegistry sessionRegistry;
    private MeetingCapabilityService capabilityService;
    private MeetingSignalingHandler handler;
    private AtomicInteger userSequence;

    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder().addModule(new JavaTimeModule()).build();
        roomService = new RoomService(CLOCK);
        sessionRegistry = new MeetingSessionRegistry(roomService);
        final MeetingProperties properties = new MeetingProperties();
        capabilityService = new MeetingCapabilityService(CLOCK, properties);
        userSequence = new AtomicInteger();
        handler = new MeetingSignalingHandler(
                sessionRegistry,
                new SignalMessageValidator(),
                objectMapper,
                CLOCK,
                properties,
                capabilityService,
                new MeetingRateLimiter(CLOCK, properties));
    }

    @Test
    void assignsIdentityAndForwardsOfferAnswerAndIceOnlyToTarget() throws Exception {
        final RoomSnapshot room = roomService.createRoom();
        final WebSocketSession first = connectedSession("session-a");
        final WebSocketSession second = connectedSession("session-b");
        final String firstId = join(first, room.roomId().value());
        final String secondId = join(second, room.roomId().value());
        clearInvocations(first, second);

        send(first, signal("OFFER", room.roomId().value(), secondId, "{\"sdp\":\"offer-sdp\"}"));
        assertSignal(second, "OFFER", firstId, "sdp", "offer-sdp");
        clearInvocations(first, second);

        send(second, signal("ANSWER", room.roomId().value(), firstId, "{\"sdp\":\"answer-sdp\"}"));
        assertSignal(first, "ANSWER", secondId, "sdp", "answer-sdp");
        clearInvocations(first, second);

        send(first, signal("ICE_CANDIDATE", room.roomId().value(), secondId,
                "{\"candidate\":\"candidate-value\",\"sdpMid\":\"0\",\"sdpMLineIndex\":0}"));
        assertSignal(second, "ICE_CANDIDATE", firstId, "candidate", "candidate-value");
    }

    @Test
    void concurrentOppositeDirectionSignalsDoNotDeadlock() throws Exception {
        final MeetingSessionRegistry concurrentRegistry = mock(MeetingSessionRegistry.class);
        final MeetingSession firstBinding = new MeetingSession(
                "session-a", "participant-a", "room-12345", new UUID(0, 1), MeetingRole.HOST);
        final MeetingSession secondBinding = new MeetingSession(
                "session-b", "participant-b", "room-12345", new UUID(0, 2), MeetingRole.PARTICIPANT);
        final MeetingSignalingHandler concurrentHandler = concurrentHandler(
                concurrentRegistry, firstBinding, secondBinding);
        final WebSocketSession first = mock(WebSocketSession.class);
        final WebSocketSession second = mock(WebSocketSession.class);
        when(first.getId()).thenReturn("session-a");
        when(second.getId()).thenReturn("session-b");
        when(first.isOpen()).thenReturn(true);
        when(second.isOpen()).thenReturn(true);
        concurrentHandler.afterConnectionEstablished(first);
        concurrentHandler.afterConnectionEstablished(second);
        final ExecutorService executor = Executors.newFixedThreadPool(2, runnable -> {
            final Thread thread = new Thread(runnable);
            thread.setDaemon(true);
            return thread;
        });

        try {
            final Future<?> firstSignal = executor.submit(() -> concurrentHandler.handleTextMessage(
                    first,
                    new TextMessage(signal("ICE_CANDIDATE", "room-12345", "participant-b",
                            "{\"candidate\":\"candidate-a\",\"sdpMid\":\"0\",\"sdpMLineIndex\":0}"))));
            final Future<?> secondSignal = executor.submit(() -> concurrentHandler.handleTextMessage(
                    second,
                    new TextMessage(signal("ICE_CANDIDATE", "room-12345", "participant-a",
                            "{\"candidate\":\"candidate-b\",\"sdpMid\":\"0\",\"sdpMLineIndex\":0}"))));

            firstSignal.get(3, TimeUnit.SECONDS);
            secondSignal.get(3, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        verify(first).sendMessage(any());
        verify(second).sendMessage(any());
    }

    private MeetingSignalingHandler concurrentHandler(
            MeetingSessionRegistry concurrentRegistry,
            MeetingSession firstBinding,
            MeetingSession secondBinding) throws Exception {
        final CountDownLatch validationsReady = new CountDownLatch(2);
        final SignalMessageValidator concurrentValidator = mock(SignalMessageValidator.class);
        org.mockito.Mockito.doAnswer(invocation -> {
            validationsReady.countDown();
            if (!validationsReady.await(2, TimeUnit.SECONDS)) {
                throw new AssertionError("Concurrent signal validation was not reached");
            }
            return null;
        }).when(concurrentValidator).validate(any(), any());
        when(concurrentRegistry.requireRoute(any(), any(), any(), any())).thenAnswer(invocation -> {
            if ("session-a".equals(invocation.getArgument(0))) {
                return new SignalRoute(firstBinding, secondBinding);
            }
            return new SignalRoute(secondBinding, firstBinding);
        });
        final MeetingProperties properties = new MeetingProperties();
        return new MeetingSignalingHandler(
                concurrentRegistry,
                concurrentValidator,
                objectMapper,
                CLOCK,
                properties,
                capabilityService,
                new MeetingRateLimiter(CLOCK, properties));
    }

    @Test
    void rejectsCrossRoomSignalingAndUnknownTarget() throws Exception {
        final RoomSnapshot firstRoom = roomService.createRoom();
        final RoomSnapshot secondRoom = roomService.createRoom();
        final WebSocketSession first = connectedSession("session-a");
        final WebSocketSession second = connectedSession("session-b");
        join(first, firstRoom.roomId().value());
        final String secondId = join(second, secondRoom.roomId().value());
        clearInvocations(first, second);

        send(first, signal("OFFER", firstRoom.roomId().value(), secondId, "{\"sdp\":\"offer-sdp\"}"));
        assertError(first, "CROSS_ROOM_SIGNALING");
        verify(second, org.mockito.Mockito.never()).sendMessage(any());
        clearInvocations(first);

        send(first, signal("OFFER", firstRoom.roomId().value(), "participant-missing",
                "{\"sdp\":\"offer-sdp\"}"));
        assertError(first, "TARGET_PARTICIPANT_NOT_FOUND");
    }

    @Test
    void rejectsUnregisteredSenderAndClientOwnedIdentity() throws Exception {
        final RoomSnapshot room = roomService.createRoom();
        final WebSocketSession unknown = connectedSession("session-unknown");

        send(unknown, signal("OFFER", room.roomId().value(), "participant-missing",
                "{\"sdp\":\"offer-sdp\"}"));
        assertError(unknown, "PARTICIPANT_NOT_REGISTERED");
        clearInvocations(unknown);

        send(unknown, "{\"version\":1,\"type\":\"JOIN\",\"roomId\":\""
                + room.roomId().value() + "\",\"senderId\":\"participant-forged\"}");
        assertError(unknown, "INVALID_MESSAGE");
    }

    @Test
    void rejectsMalformedUnsupportedWrongVersionAndOversizedMessages() throws Exception {
        final WebSocketSession session = connectedSession("session-a");

        send(session, "{not-json");
        assertError(session, "INVALID_MESSAGE");
        clearInvocations(session);

        send(session, "{\"version\":1,\"type\":\"CHAT\",\"roomId\":\"room-12345\"}");
        assertError(session, "UNSUPPORTED_SIGNAL_TYPE");
        clearInvocations(session);

        send(session, "{\"version\":2,\"type\":\"JOIN\",\"roomId\":\"room-12345\"}");
        assertError(session, "UNSUPPORTED_PROTOCOL_VERSION");
        clearInvocations(session);

        send(session, "{\"version\":1.5,\"type\":\"JOIN\",\"roomId\":\"room-12345\"}");
        assertError(session, "UNSUPPORTED_PROTOCOL_VERSION");
        clearInvocations(session);

        send(session, "x".repeat(65_537));
        assertError(session, "MESSAGE_TOO_LARGE");
        clearInvocations(session);

        send(session, "я".repeat(40_000));
        assertError(session, "MESSAGE_TOO_LARGE");
    }

    @Test
    void rejectsOversizedSdpAndInvalidIceShape() throws Exception {
        final RoomSnapshot room = roomService.createRoom();
        final WebSocketSession first = connectedSession("session-a");
        final WebSocketSession second = connectedSession("session-b");
        join(first, room.roomId().value());
        final String secondId = join(second, room.roomId().value());
        clearInvocations(first, second);

        send(first, signal("OFFER", room.roomId().value(), secondId,
                "{\"sdp\":\"" + "s".repeat(SignalMessageValidator.MAX_SDP_CHARS + 1) + "\"}"));
        assertError(first, "SIGNAL_FIELD_TOO_LARGE");
        clearInvocations(first);

        send(first, signal("ICE_CANDIDATE", room.roomId().value(), secondId,
                "{\"candidate\":\"candidate-value\",\"sdpMid\":\"0\",\"sdpMLineIndex\":129}"));
        assertError(first, "INVALID_MESSAGE");
        clearInvocations(first);

        send(first, signal("ICE_CANDIDATE", room.roomId().value(), secondId,
                "{\"candidate\":\"candidate-value\",\"sdpMid\":\"0\",\"sdpMLineIndex\":0.5}"));
        assertError(first, "INVALID_MESSAGE");
    }

    @Test
    void duplicateJoinAndLeaveAreIdempotent() throws Exception {
        final RoomSnapshot room = roomService.createRoom();
        final WebSocketSession session = connectedSession("session-a");
        final String participantId = join(session, room.roomId().value());
        clearInvocations(session);

        final String duplicateParticipantId = join(session, room.roomId().value());
        assertThat(duplicateParticipantId).isEqualTo(participantId);
        assertThat(roomService.getRoom(room.roomId().value()).participants()).hasSize(1);
        clearInvocations(session);

        send(session, baseMessage("LEAVE", room.roomId().value()));
        assertThat(lastMessage(session).path("payload").path("released").booleanValue()).isTrue();
        clearInvocations(session);

        send(session, baseMessage("LEAVE", room.roomId().value()));
        assertThat(lastMessage(session).path("payload").path("released").booleanValue()).isFalse();
        assertThat(sessionRegistry.sessionCount()).isZero();
    }

    @Test
    void rejectsOfferFromServerSelectedAnswerer() throws Exception {
        final RoomSnapshot room = roomService.createRoom();
        final WebSocketSession first = connectedSession("session-a");
        final WebSocketSession second = connectedSession("session-b");
        final String firstId = join(first, room.roomId().value());
        join(second, room.roomId().value());
        clearInvocations(first, second);

        send(second, signal("OFFER", room.roomId().value(), firstId, "{\"sdp\":\"glare-offer\"}"));

        assertError(second, "INVALID_NEGOTIATION_ROLE");
        verify(first, org.mockito.Mockito.never()).sendMessage(any());
    }

    @Test
    void disconnectReleasesParticipantAndNotifiesPeer() throws Exception {
        final RoomSnapshot room = roomService.createRoom();
        final WebSocketSession first = connectedSession("session-a");
        final WebSocketSession second = connectedSession("session-b");
        join(first, room.roomId().value());
        final String secondId = join(second, room.roomId().value());
        clearInvocations(first, second);

        handler.afterConnectionClosed(second, CloseStatus.GOING_AWAY);

        assertSignal(first, "PARTICIPANT_LEFT", secondId, "participantId", secondId);
        assertThat(roomService.getRoom(room.roomId().value()).participants()).hasSize(1);

        handler.afterConnectionClosed(first, CloseStatus.NORMAL);
        assertThat(roomService.roomCount()).isZero();
        assertThat(sessionRegistry.sessionCount()).isZero();
    }

    @Test
    void revocationDisconnectsAuthenticatedMemberAndReleasesSlot() throws Exception {
        final RoomSnapshot room = roomService.createRoom();
        final WebSocketSession host = connectedSession("session-host");
        final WebSocketSession member = connectedSession("session-member");
        join(host, room.roomId().value());
        final String memberId = join(member, room.roomId().value());
        clearInvocations(host, member);

        handler.revoke(room.roomId().value(), new UUID(0, 2));

        assertError(member, "ACCESS_REVOKED");
        verify(member).close(CloseStatus.POLICY_VIOLATION);
        assertSignal(host, "PARTICIPANT_LEFT", memberId, "participantId", memberId);
        assertThat(sessionRegistry.findBySession("session-member")).isEmpty();
        assertThat(roomService.getRoom(room.roomId().value()).participants()).hasSize(1);
    }

    @Test
    void thirdParticipantReceivesRoomFullWithoutOccupyingSlot() throws Exception {
        final RoomSnapshot room = roomService.createRoom();
        final WebSocketSession first = connectedSession("session-a");
        final WebSocketSession second = connectedSession("session-b");
        final WebSocketSession third = connectedSession("session-c");
        join(first, room.roomId().value());
        join(second, room.roomId().value());
        clearInvocations(third);

        send(third, joinMessage(room.roomId().value()));

        assertSignalTypeAndCode(third, "ROOM_FULL", "ROOM_FULL");
        assertThat(roomService.getRoom(room.roomId().value()).participants()).hasSize(2);
        assertThat(sessionRegistry.sessionCount()).isEqualTo(2);
    }

    @Test
    void compensatesClosedPeerDuringJoinWithoutLeavingGhostOccupancy() throws Exception {
        final RoomSnapshot room = roomService.createRoom();
        final WebSocketSession first = connectedSession("session-a");
        final WebSocketSession second = connectedSession("session-b");
        join(first, room.roomId().value());
        when(first.isOpen()).thenReturn(false);

        final String secondId = join(second, room.roomId().value());

        assertThat(lastMessage(second).path("payload").path("participantId").textValue())
                .isEqualTo(secondId);
        assertThat(lastMessage(second).path("payload").path("peerId").isNull()).isTrue();
        assertThat(roomService.getRoom(room.roomId().value()).participants()).hasSize(1);
        assertThat(sessionRegistry.sessionCount()).isEqualTo(1);
    }

    private WebSocketSession connectedSession(String sessionId) {
        final WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(sessionId);
        when(session.isOpen()).thenReturn(true);
        handler.afterConnectionEstablished(session);
        return session;
    }

    private String join(WebSocketSession session, String roomId) throws Exception {
        send(session, joinMessage(roomId));
        final JsonNode joined = lastMessage(session);
        assertThat(joined.path("type").textValue()).isEqualTo("JOINED");
        assertThat(joined.path("version").intValue()).isEqualTo(1);
        return joined.path("payload").path("participantId").textValue();
    }

    @Test
    void accountRevocationClosesAnExistingSocketAndRejectsPreissuedGrant() throws Exception {
        final var status = mock(com.adventacademy.security.AccessTokenStatusClient.class);
        handler.accessStatus(status);
        final var room = roomService.createRoom().roomId().value();
        final var session = connectedSession("revoked-session");
        final var user = UUID.randomUUID();
        final var grant = capabilityService.issueScheduled(room, user, MeetingRole.HOST, null, "Bearer access");
        send(session, "{\"version\":1,\"type\":\"JOIN\",\"roomId\":\"" + room + "\",\"capability\":\"" + grant.token() + "\"}");
        final var pending = capabilityService.issueScheduled(room, UUID.randomUUID(), MeetingRole.PARTICIPANT, null, "Bearer access");
        org.mockito.Mockito.doThrow(new com.adventacademy.security.AccessTokenStatusClient.InactiveTokenException())
                .when(status).requireActive("access");
        handler.revokeInactiveSessions();
        verify(session).close(CloseStatus.POLICY_VIOLATION);
        assertThat(sessionRegistry.findBySession(session.getId())).isEmpty();
        final var newcomer = connectedSession("pending-session");
        send(newcomer, "{\"version\":1,\"type\":\"JOIN\",\"roomId\":\"" + room + "\",\"capability\":\"" + pending.token() + "\"}");
        assertError(newcomer, "ACCESS_REVOKED");
        assertThat(sessionRegistry.findBySession(newcomer.getId())).isEmpty();
    }

    @Test
    void renewedCapabilityUpdatesAuthorizationWithoutRejoiningTheRoom() throws Exception {
        final var status = mock(com.adventacademy.security.AccessTokenStatusClient.class);
        handler.accessStatus(status);
        final var room = roomService.createRoom().roomId().value();
        final var session = connectedSession("renewed-session");
        final var user = UUID.randomUUID();
        final var first = capabilityService.issueScheduled(room, user, MeetingRole.HOST, null, "Bearer old");
        send(session, "{\"version\":1,\"type\":\"JOIN\",\"roomId\":\"" + room + "\",\"capability\":\"" + first.token() + "\"}");
        final var next = capabilityService.issueScheduled(room, user, MeetingRole.HOST, null, "Bearer new");
        send(session, "{\"version\":1,\"type\":\"REAUTHORIZE\",\"roomId\":\"" + room + "\",\"capability\":\"" + next.token() + "\"}");
        org.mockito.Mockito.doThrow(new com.adventacademy.security.AccessTokenStatusClient.InactiveTokenException())
                .when(status).requireActive("old");
        send(session, baseMessage("PING", room));
        assertThat(lastMessage(session).path("type").asText()).isEqualTo("PONG");
        assertThat(roomService.getRoom(room).participants()).hasSize(1);
    }

    private String joinMessage(String roomId) {
        final int userIndex = userSequence.incrementAndGet();
        MeetingRole role = MeetingRole.PARTICIPANT;
        if (userIndex == 1) {
            role = MeetingRole.HOST;
        }
        final String capability = capabilityService.issue(
                roomId,
                new UUID(0, userIndex),
                role).token();
        return "{\"version\":1,\"type\":\"JOIN\",\"roomId\":\""
                + roomId + "\",\"capability\":\"" + capability + "\"}";
    }

    private void send(WebSocketSession session, String message) throws Exception {
        handler.handleTextMessage(session, new TextMessage(message));
    }

    private String baseMessage(String type, String roomId) {
        return "{\"version\":1,\"type\":\"" + type + "\",\"roomId\":\"" + roomId + "\"}";
    }

    private String signal(String type, String roomId, String targetId, String payload) {
        return "{\"version\":1,\"type\":\"" + type + "\",\"roomId\":\"" + roomId
                + "\",\"targetId\":\"" + targetId + "\",\"payload\":" + payload + "}";
    }

    private void assertError(WebSocketSession session, String code) throws Exception {
        assertSignalTypeAndCode(session, "ERROR", code);
    }

    private void assertSignalTypeAndCode(WebSocketSession session, String type, String code) throws Exception {
        final JsonNode message = lastMessage(session);
        assertThat(message.path("type").textValue()).isEqualTo(type);
        assertThat(message.path("payload").path("code").textValue()).isEqualTo(code);
    }

    private void assertSignal(
            WebSocketSession session,
            String type,
            String senderId,
            String payloadField,
            String payloadValue) throws Exception {
        final JsonNode message = lastMessage(session);
        assertThat(message.path("type").textValue()).isEqualTo(type);
        assertThat(message.path("senderId").textValue()).isEqualTo(senderId);
        assertThat(message.path("payload").path(payloadField).textValue()).isEqualTo(payloadValue);
    }

    private JsonNode lastMessage(WebSocketSession session) throws Exception {
        final ArgumentCaptor<WebSocketMessage<?>> captor = ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(session, org.mockito.Mockito.atLeastOnce()).sendMessage(captor.capture());
        final List<WebSocketMessage<?>> messages = captor.getAllValues();
        return objectMapper.readTree(messages.getLast().getPayload().toString());
    }
}
