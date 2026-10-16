package com.adventacademy.meetingservice.adapter.websocket;

import com.adventacademy.meetingservice.application.security.MeetingAccessService;
import com.adventacademy.meetingservice.application.service.RoomService;
import com.adventacademy.meetingservice.domain.model.RoomSnapshot;
import com.adventacademy.meetingservice.domain.model.RoomStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MeetingWebSocketIT {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private com.adventacademy.security.AccessTokenStatusClient accessTokenStatusClient;

    @LocalServerPort
    private int port;

    @Autowired
    private MeetingAccessService meetingAccessService;

    @Autowired
    private RoomService roomService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void joinsForwardsSignalsAndCleansUpUnexpectedDisconnect() throws Exception {
        final UUID firstUser = UUID.randomUUID();
        final UUID secondUser = UUID.randomUUID();
        final RoomSnapshot room = createRoom(firstUser, secondUser);
        final List<TestClient> clients = new ArrayList<>();
        try {
            final TestClient first = connect(clients);
            final TestClient second = connect(clients);

            first.send(join(room.roomId().value(), capability(room.roomId().value(), firstUser)));
            final JsonNode firstJoined = first.nextOfType("JOINED");
            final String firstId = firstJoined.path("payload").path("participantId").textValue();
            assertThat(firstJoined.path("payload").path("peerId").isNull()).isTrue();

            second.send(join(room.roomId().value(), capability(room.roomId().value(), secondUser)));
            final JsonNode secondJoined = second.nextOfType("JOINED");
            final String secondId = secondJoined.path("payload").path("participantId").textValue();
            assertThat(secondJoined.path("payload").path("peerId").textValue()).isEqualTo(firstId);
            assertThat(first.nextOfType("PARTICIPANT_JOINED").path("payload").path("initiator").booleanValue())
                    .isTrue();

            first.send(signal("OFFER", room.roomId().value(), secondId,
                    "{\"sdp\":\"offer-sdp\"}"));
            assertForwarded(second, "OFFER", firstId, "sdp", "offer-sdp");

            second.send(signal("ANSWER", room.roomId().value(), firstId,
                    "{\"sdp\":\"answer-sdp\"}"));
            assertForwarded(first, "ANSWER", secondId, "sdp", "answer-sdp");

            first.send(signal("ICE_CANDIDATE", room.roomId().value(), secondId,
                    "{\"candidate\":\"candidate-a\",\"sdpMid\":\"0\",\"sdpMLineIndex\":0}"));
            assertForwarded(second, "ICE_CANDIDATE", firstId, "candidate", "candidate-a");

            second.close(CloseStatus.GOING_AWAY);
            final JsonNode participantLeft = first.nextOfType("PARTICIPANT_LEFT");
            assertThat(participantLeft.path("payload").path("participantId").textValue())
                    .isEqualTo(secondId);

            final RoomSnapshot afterDisconnect = roomService.getRoom(room.roomId().value());
            assertThat(afterDisconnect.status()).isEqualTo(RoomStatus.WAITING);
            assertThat(afterDisconnect.participants()).hasSize(1);
        } finally {
            closeAll(clients);
        }
    }

    @Test
    void rejectsThirdParticipantWithControlledRoomFullSignal() throws Exception {
        final UUID firstUser = UUID.randomUUID();
        final UUID secondUser = UUID.randomUUID();
        final UUID thirdUser = UUID.randomUUID();
        final RoomSnapshot room = createRoom(firstUser, secondUser, thirdUser);
        final List<TestClient> clients = new ArrayList<>();
        try {
            final TestClient first = connect(clients);
            final TestClient second = connect(clients);
            final TestClient third = connect(clients);
            first.send(join(room.roomId().value(), capability(room.roomId().value(), firstUser)));
            first.nextOfType("JOINED");
            second.send(join(room.roomId().value(), capability(room.roomId().value(), secondUser)));
            second.nextOfType("JOINED");
            first.nextOfType("PARTICIPANT_JOINED");

            third.send(join(room.roomId().value(), capability(room.roomId().value(), thirdUser)));

            final JsonNode roomFull = third.nextOfType("ROOM_FULL");
            assertThat(roomFull.path("payload").path("code").textValue()).isEqualTo("ROOM_FULL");
            final RoomSnapshot response = roomService.getRoom(room.roomId().value());
            assertThat(response.participants()).hasSize(2);
        } finally {
            closeAll(clients);
        }
    }

    @Test
    void rejectsCapabilityReplayAndCrossRoomUse() throws Exception {
        final UUID firstUser = UUID.randomUUID();
        final UUID secondUser = UUID.randomUUID();
        final RoomSnapshot firstRoom = createRoom(firstUser);
        final RoomSnapshot secondRoom = createRoom(secondUser);
        final String capability = capability(firstRoom.roomId().value(), firstUser);
        final List<TestClient> clients = new ArrayList<>();
        try {
            final TestClient first = connect(clients);
            first.send(join(firstRoom.roomId().value(), capability));
            first.nextOfType("JOINED");

            final TestClient replay = connect(clients);
            replay.send(join(firstRoom.roomId().value(), capability));
            assertThat(replay.nextOfType("ERROR").path("payload").path("code").textValue())
                    .isEqualTo("INVALID_CAPABILITY");

            final String secondCapability = capability(secondRoom.roomId().value(), secondUser);
            final TestClient crossRoom = connect(clients);
            crossRoom.send(join(firstRoom.roomId().value(), secondCapability));
            assertThat(crossRoom.nextOfType("ERROR").path("payload").path("code").textValue())
                    .isEqualTo("INVALID_CAPABILITY");
        } finally {
            closeAll(clients);
        }
    }

    private RoomSnapshot createRoom(UUID hostId, UUID... memberIds) {
        final RoomSnapshot room = meetingAccessService.createPrivateMeeting(hostId);
        for (UUID memberId : memberIds) {
            meetingAccessService.addMember(room.roomId().value(), hostId, memberId);
        }
        return room;
    }

    private TestClient connect(List<TestClient> clients) throws Exception {
        final TestClient client = new TestClient(objectMapper);
        final String endpoint = "ws://localhost:" + port + "/ws/meetings";
        final WebSocketSession session = new StandardWebSocketClient()
                .execute(client, endpoint)
                .get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        client.attach(session);
        clients.add(client);
        return client;
    }

    private void assertForwarded(
            TestClient client,
            String type,
            String senderId,
            String payloadField,
            String payloadValue) throws Exception {
        final JsonNode message = client.nextOfType(type);
        assertThat(message.path("senderId").textValue()).isEqualTo(senderId);
        assertThat(message.path("payload").path(payloadField).textValue()).isEqualTo(payloadValue);
    }

    private String capability(String roomId, UUID userId) {
        return meetingAccessService.issueCapability(roomId, userId, "Bearer fixture-token").token();
    }

    private String join(String roomId, String capability) {
        return "{\"version\":1,\"type\":\"JOIN\",\"roomId\":\"" + roomId
                + "\",\"capability\":\"" + capability + "\"}";
    }

    private String signal(String type, String roomId, String targetId, String payload) {
        return "{\"version\":1,\"type\":\"" + type + "\",\"roomId\":\"" + roomId
                + "\",\"targetId\":\"" + targetId + "\",\"payload\":" + payload + "}";
    }

    private void closeAll(List<TestClient> clients) throws Exception {
        for (TestClient client : clients) {
            client.close(CloseStatus.NORMAL);
        }
    }

    private static final class TestClient extends TextWebSocketHandler {

        private final BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        private final ObjectMapper objectMapper;
        private WebSocketSession session;

        private TestClient(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        private void attach(WebSocketSession session) {
            this.session = session;
        }

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) {
            messages.add(message.getPayload());
        }

        private void send(String payload) throws Exception {
            session.sendMessage(new TextMessage(payload));
        }

        private JsonNode nextOfType(String expectedType) throws Exception {
            final long deadline = System.nanoTime() + TIMEOUT.toNanos();
            while (System.nanoTime() < deadline) {
                final long remaining = deadline - System.nanoTime();
                final String payload = messages.poll(remaining, TimeUnit.NANOSECONDS);
                if (payload == null) {
                    break;
                }
                final JsonNode message = objectMapper.readTree(payload);
                if (expectedType.equals(message.path("type").textValue())) {
                    return message;
                }
            }
            throw new AssertionError("Timed out waiting for signal type " + expectedType);
        }

        private void close(CloseStatus status) throws Exception {
            if (session != null && session.isOpen()) {
                session.close(status);
            }
        }
    }
}
