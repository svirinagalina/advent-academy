package com.adventacademy.meetingservice.adapter.websocket;

import com.adventacademy.meetingservice.application.service.MeetingSessionRegistry;
import com.adventacademy.meetingservice.application.service.RoomService;
import com.adventacademy.meetingservice.application.security.MeetingCapabilityService;
import com.adventacademy.meetingservice.application.security.MeetingGrant;
import com.adventacademy.meetingservice.application.security.MeetingRateLimiter;
import com.adventacademy.meetingservice.application.session.SessionRegistration;
import com.adventacademy.meetingservice.domain.model.MeetingRole;
import com.adventacademy.meetingservice.infrastructure.config.MeetingProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.ArgumentMatchers.any;
import static org.assertj.core.api.Assertions.assertThat;

class ConcurrentJoinTest {
    @Test
    void concurrentJoinsMustNotTellTheAnswererToOffer() throws Exception {
        final Clock clock = Clock.systemUTC();
        final var rooms = new RoomService(clock);
        final var registry = concurrentRegistry(rooms);
        final var properties = new MeetingProperties();
        final var capabilities = new MeetingCapabilityService(clock, properties);
        final var mapper = JsonMapper.builder().addModule(new JavaTimeModule()).build();
        final var handler = new MeetingSignalingHandler(registry, new SignalMessageValidator(), mapper, clock, properties,
                capabilities, new MeetingRateLimiter(clock, properties));
        final String room = rooms.createRoom().roomId().value();
        final List<List<JsonNode>> received = List.of(new CopyOnWriteArrayList<>(), new CopyOnWriteArrayList<>());
        try (var pool = Executors.newFixedThreadPool(2)) {
            final List<Future<?>> tasks = new ArrayList<>();
            for (int index = 0; index < 2; index++) {
                final int number = index;
                final WebSocketSession session = mock(WebSocketSession.class);
                when(session.getId()).thenReturn("concurrent-" + index);
                when(session.isOpen()).thenReturn(true);
                doAnswer(invocation -> {
                    received.get(number).add(mapper.readTree(((TextMessage) invocation.getArgument(0)).getPayload()));
                    return null;
                }).when(session).sendMessage(any());
                handler.afterConnectionEstablished(session);
                final String token = capabilities.issue(room, UUID.randomUUID(), MeetingRole.PARTICIPANT).token();
                final String join = mapper.writeValueAsString(Map.of("version", 1, "type", "JOIN", "roomId", room, "capability", token));
                tasks.add(pool.submit(() -> handler.handleTextMessage(session, new TextMessage(join))));
            }
            for (final var task : tasks) {
                task.get(8, TimeUnit.SECONDS);
            }
        }
        for (final var messages : received) {
            final var joined = messages.stream().filter(message -> message.path("type").asText().equals("JOINED"))
                    .findFirst().orElseThrow();
            final boolean answerer = !joined.path("payload").path("peers").get(0).path("initiator").asBoolean();
            final boolean toldToOffer = messages.stream().anyMatch(message -> message.path("type").asText().equals("PARTICIPANT_JOINED")
                    && message.path("payload").path("initiator").asBoolean());
            assertThat(answerer && toldToOffer).as("Contradictory initiator notification: %s", messages).isFalse();
        }
    }
    private MeetingSessionRegistry concurrentRegistry(RoomService rooms) {
        final var bothRegistered = new CountDownLatch(2);
        return new MeetingSessionRegistry(rooms) {
            @Override
            public SessionRegistration join(String id, MeetingGrant grant) {
                final var result = super.join(id, grant);
                bothRegistered.countDown();
                try {
                    if (!bothRegistered.await(5, TimeUnit.SECONDS)) {
                        throw new AssertionError("Concurrent fixture failed");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(exception);
                }
                return result;
            }
        };
    }
}
