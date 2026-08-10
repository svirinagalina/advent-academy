package com.adventacademy.meetingservice.adapter.websocket;

import com.adventacademy.meetingservice.application.service.RoomService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MeetingSignalingHandlerTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-10T10:00:00Z"), ZoneOffset.UTC);

    private ObjectMapper objectMapper;
    private RoomService roomService;
    private MeetingSignalingHandler handler;

    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder().addModule(new JavaTimeModule()).build();
        roomService = new RoomService(CLOCK);
        final MeetingProperties properties = new MeetingProperties();
        handler = new MeetingSignalingHandler(
                roomService,
                new SignalMessageValidator(),
                objectMapper,
                CLOCK,
                properties);
    }

    @Test
    void forwardsOfferAnswerAndIceOnlyToTarget() throws Exception {
        final RoomSnapshot room = roomService.createRoom();
        final WebSocketSession first = connectedSession("session-a");
        final WebSocketSession second = connectedSession("session-b");
        join(first, room.roomId().value(), "participant-a");
        join(second, room.roomId().value(), "participant-b");
        clearInvocations(first, second);

        send(first, signal("OFFER", room.roomId().value(), "participant-a", "participant-b",
                "{\"sdp\":\"offer-sdp\"}"));
        assertSignal(second, "OFFER", "participant-a", "offer-sdp");
        clearInvocations(first, second);

        send(second, signal("ANSWER", room.roomId().value(), "participant-b", "participant-a",
                "{\"sdp\":\"answer-sdp\"}"));
        assertSignal(first, "ANSWER", "participant-b", "answer-sdp");
        clearInvocations(first, second);

        send(first, signal("ICE_CANDIDATE", room.roomId().value(), "participant-a", "participant-b",
                "{\"candidate\":\"candidate-value\",\"sdpMid\":\"0\",\"sdpMLineIndex\":0}"));
        assertSignal(second, "ICE_CANDIDATE", "participant-a", "candidate-value");
    }

    @Test
    void rejectsCrossRoomSignaling() throws Exception {
        final RoomSnapshot firstRoom = roomService.createRoom();
        final RoomSnapshot secondRoom = roomService.createRoom();
        final WebSocketSession first = connectedSession("session-a");
        final WebSocketSession second = connectedSession("session-b");
        join(first, firstRoom.roomId().value(), "participant-a");
        join(second, secondRoom.roomId().value(), "participant-b");
        clearInvocations(first, second);

        send(first, signal("OFFER", firstRoom.roomId().value(), "participant-a", "participant-b",
                "{\"sdp\":\"offer-sdp\"}"));

        assertError(first, "CROSS_ROOM_SIGNALING");
        verify(second, org.mockito.Mockito.never()).sendMessage(any());
    }

    @Test
    void rejectsUnknownSenderAndUnknownTarget() throws Exception {
        final RoomSnapshot room = roomService.createRoom();
        final WebSocketSession unknown = connectedSession("session-unknown");

        send(unknown, signal("OFFER", room.roomId().value(), "participant-x", "participant-y",
                "{\"sdp\":\"offer-sdp\"}"));
        assertError(unknown, "PARTICIPANT_NOT_REGISTERED");
        clearInvocations(unknown);

        join(unknown, room.roomId().value(), "participant-x");
        clearInvocations(unknown);
        send(unknown, signal("OFFER", room.roomId().value(), "participant-x", "participant-y",
                "{\"sdp\":\"offer-sdp\"}"));
        assertError(unknown, "TARGET_PARTICIPANT_NOT_FOUND");
    }

    @Test
    void rejectsSenderImpersonation() throws Exception {
        final RoomSnapshot room = roomService.createRoom();
        final WebSocketSession first = connectedSession("session-a");
        final WebSocketSession second = connectedSession("session-b");
        join(first, room.roomId().value(), "participant-a");
        join(second, room.roomId().value(), "participant-b");
        clearInvocations(first, second);

        send(first, signal("OFFER", room.roomId().value(), "participant-b", "participant-b",
                "{\"sdp\":\"offer-sdp\"}"));

        assertError(first, "SENDER_MISMATCH");
        verify(second, org.mockito.Mockito.never()).sendMessage(any());
    }

    @Test
    void handlesMalformedAndUnsupportedMessages() throws Exception {
        final WebSocketSession session = connectedSession("session-a");

        send(session, "{not-json");
        assertError(session, "INVALID_MESSAGE");
        clearInvocations(session);

        send(session, "{\"type\":\"CHAT\",\"roomId\":\"room-12345\"}");
        assertError(session, "UNSUPPORTED_SIGNAL_TYPE");
        clearInvocations(session);

        send(session, "{\"type\":\"OFFER\",\"roomId\":\"room-12345\","
                + "\"targetId\":\"participant-b\",\"payload\":{}}");
        assertError(session, "INVALID_MESSAGE");
    }

    @Test
    void disconnectReleasesParticipantAndNotifiesPeer() throws Exception {
        final RoomSnapshot room = roomService.createRoom();
        final WebSocketSession first = connectedSession("session-a");
        final WebSocketSession second = connectedSession("session-b");
        join(first, room.roomId().value(), "participant-a");
        join(second, room.roomId().value(), "participant-b");
        clearInvocations(first, second);

        handler.afterConnectionClosed(second, CloseStatus.GOING_AWAY);

        assertSignal(first, "PARTICIPANT_LEFT", "participant-b", "participant-b");
        assertThat(roomService.getRoom(room.roomId().value()).participants())
                .extracting(participantId -> participantId.value())
                .containsExactly("participant-a");

        handler.afterConnectionClosed(first, CloseStatus.NORMAL);
        assertThat(roomService.roomCount()).isZero();
    }

    @Test
    void thirdParticipantReceivesRoomFullWithoutOccupyingSlot() throws Exception {
        final RoomSnapshot room = roomService.createRoom();
        final WebSocketSession first = connectedSession("session-a");
        final WebSocketSession second = connectedSession("session-b");
        final WebSocketSession third = connectedSession("session-c");
        join(first, room.roomId().value(), "participant-a");
        join(second, room.roomId().value(), "participant-b");
        clearInvocations(third);

        join(third, room.roomId().value(), "participant-c");

        assertSignalTypeAndCode(third, "ROOM_FULL", "ROOM_FULL");
        assertThat(roomService.getRoom(room.roomId().value()).participants()).hasSize(2);

        handler.afterConnectionClosed(first, CloseStatus.NORMAL);
        clearInvocations(third);
        join(third, room.roomId().value(), "participant-c");
        assertThat(lastMessage(third).path("type").textValue()).isEqualTo("JOINED");
        assertThat(roomService.getRoom(room.roomId().value()).participants()).hasSize(2);
    }

    private WebSocketSession connectedSession(String sessionId) {
        final WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(sessionId);
        when(session.isOpen()).thenReturn(true);
        handler.afterConnectionEstablished(session);
        return session;
    }

    private void join(WebSocketSession session, String roomId, String participantId) throws Exception {
        send(session, "{\"type\":\"JOIN\",\"roomId\":\"" + roomId + "\",\"senderId\":\""
                + participantId + "\"}");
    }

    private void send(WebSocketSession session, String message) throws Exception {
        handler.handleTextMessage(session, new TextMessage(message));
    }

    private String signal(String type, String roomId, String senderId, String targetId, String payload) {
        return "{\"type\":\"" + type + "\",\"roomId\":\"" + roomId + "\",\"senderId\":\""
                + senderId + "\",\"targetId\":\"" + targetId + "\",\"payload\":" + payload + "}";
    }

    private void assertError(WebSocketSession session, String code) throws Exception {
        assertSignalTypeAndCode(session, "ERROR", code);
    }

    private void assertSignalTypeAndCode(WebSocketSession session, String type, String code) throws Exception {
        final JsonNode message = lastMessage(session);
        assertThat(message.path("type").textValue()).isEqualTo(type);
        assertThat(message.path("payload").path("code").textValue()).isEqualTo(code);
    }

    private void assertSignal(WebSocketSession session, String type, String senderId, String payloadValue)
            throws Exception {
        final JsonNode message = lastMessage(session);
        assertThat(message.path("type").textValue()).isEqualTo(type);
        assertThat(message.path("senderId").textValue()).isEqualTo(senderId);
        final List<String> possibleFields = List.of("sdp", "candidate", "participantId");
        assertThat(possibleFields.stream()
                .map(field -> message.path("payload").path(field).textValue())
                .filter(value -> value != null)
                .toList()).contains(payloadValue);
    }

    private JsonNode lastMessage(WebSocketSession session) throws Exception {
        final ArgumentCaptor<WebSocketMessage<?>> captor = ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(session, org.mockito.Mockito.atLeastOnce()).sendMessage(captor.capture());
        final WebSocketMessage<?> message = captor.getAllValues().getLast();
        return objectMapper.readTree(message.getPayload().toString());
    }
}
