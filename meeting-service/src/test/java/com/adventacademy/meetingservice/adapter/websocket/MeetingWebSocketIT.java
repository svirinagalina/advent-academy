package com.adventacademy.meetingservice.adapter.websocket;

import com.adventacademy.meetingservice.adapter.rest.dto.RoomResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;
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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MeetingWebSocketIT {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void joinsForwardsSignalsAndCleansUpUnexpectedDisconnect() throws Exception {
        final RoomResponse room = createRoom();
        final List<TestClient> clients = new ArrayList<>();
        try {
            final TestClient first = connect(clients);
            final TestClient second = connect(clients);

            first.send(join(room.roomId(), "participant-a"));
            assertThat(first.nextOfType("JOINED").path("payload").path("peerId").isNull()).isTrue();

            second.send(join(room.roomId(), "participant-b"));
            assertThat(second.nextOfType("JOINED").path("payload").path("peerId").textValue())
                    .isEqualTo("participant-a");
            assertThat(first.nextOfType("PARTICIPANT_JOINED").path("payload").path("initiator").booleanValue())
                    .isTrue();

            first.send(signal("OFFER", room.roomId(), "participant-a", "participant-b",
                    "{\"sdp\":\"offer-sdp\"}"));
            assertForwarded(second, "OFFER", "participant-a", "sdp", "offer-sdp");

            second.send(signal("ANSWER", room.roomId(), "participant-b", "participant-a",
                    "{\"sdp\":\"answer-sdp\"}"));
            assertForwarded(first, "ANSWER", "participant-b", "sdp", "answer-sdp");

            first.send(signal("ICE_CANDIDATE", room.roomId(), "participant-a", "participant-b",
                    "{\"candidate\":\"candidate-a\",\"sdpMid\":\"0\",\"sdpMLineIndex\":0}"));
            assertForwarded(second, "ICE_CANDIDATE", "participant-a", "candidate", "candidate-a");

            second.close(CloseStatus.GOING_AWAY);
            final JsonNode participantLeft = first.nextOfType("PARTICIPANT_LEFT");
            assertThat(participantLeft.path("payload").path("participantId").textValue())
                    .isEqualTo("participant-b");

            final RoomResponse afterDisconnect = restTemplate.getForObject(
                    "/api/meetings/rooms/{roomId}", RoomResponse.class, room.roomId());
            assertThat(afterDisconnect.status()).isEqualTo("WAITING");
            assertThat(afterDisconnect.participantCount()).isEqualTo(1);
        } finally {
            closeAll(clients);
        }
    }

    @Test
    void rejectsThirdParticipantWithControlledRoomFullSignal() throws Exception {
        final RoomResponse room = createRoom();
        final List<TestClient> clients = new ArrayList<>();
        try {
            final TestClient first = connect(clients);
            final TestClient second = connect(clients);
            final TestClient third = connect(clients);
            first.send(join(room.roomId(), "participant-a"));
            first.nextOfType("JOINED");
            second.send(join(room.roomId(), "participant-b"));
            second.nextOfType("JOINED");
            first.nextOfType("PARTICIPANT_JOINED");

            third.send(join(room.roomId(), "participant-c"));

            final JsonNode roomFull = third.nextOfType("ROOM_FULL");
            assertThat(roomFull.path("payload").path("code").textValue()).isEqualTo("ROOM_FULL");
            final RoomResponse response = restTemplate.getForObject(
                    "/api/meetings/rooms/{roomId}", RoomResponse.class, room.roomId());
            assertThat(response.participantCount()).isEqualTo(2);
        } finally {
            closeAll(clients);
        }
    }

    private RoomResponse createRoom() {
        final ResponseEntity<RoomResponse> response = restTemplate.postForEntity(
                "/api/meetings/rooms", null, RoomResponse.class);
        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
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

    private String join(String roomId, String participantId) {
        return "{\"type\":\"JOIN\",\"roomId\":\"" + roomId + "\",\"senderId\":\""
                + participantId + "\"}";
    }

    private String signal(String type, String roomId, String senderId, String targetId, String payload) {
        return "{\"type\":\"" + type + "\",\"roomId\":\"" + roomId + "\",\"senderId\":\""
                + senderId + "\",\"targetId\":\"" + targetId + "\",\"payload\":" + payload + "}";
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
