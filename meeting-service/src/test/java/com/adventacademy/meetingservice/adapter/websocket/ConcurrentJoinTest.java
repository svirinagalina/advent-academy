package com.adventacademy.meetingservice.adapter.websocket;

import com.adventacademy.meetingservice.application.service.*;
import com.adventacademy.meetingservice.application.security.*;
import com.adventacademy.meetingservice.domain.model.MeetingRole;
import com.adventacademy.meetingservice.infrastructure.config.MeetingProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.assertj.core.api.Assertions.assertThat;

class ConcurrentJoinTest {
    @Test
    void concurrentJoinsMustNotTellTheAnswererToOffer() throws Exception {
        Clock clock=Clock.systemUTC();
        var rooms=new RoomService(clock);
        var bothRegistered=new CountDownLatch(2);
        var registry=new MeetingSessionRegistry(rooms) {
            @Override public com.adventacademy.meetingservice.application.session.SessionRegistration join(String id,MeetingGrant grant) {
                var result=super.join(id,grant);
                bothRegistered.countDown();
                try {if(!bothRegistered.await(5,TimeUnit.SECONDS))throw new AssertionError("Concurrent fixture failed");}
                catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}
                return result;
            }
        };
        var properties=new MeetingProperties();
        var capabilities=new MeetingCapabilityService(clock,properties);
        var mapper=JsonMapper.builder().addModule(new JavaTimeModule()).build();
        var handler=new MeetingSignalingHandler(registry,new SignalMessageValidator(),mapper,clock,properties,
                capabilities,new MeetingRateLimiter(clock,properties));
        String room=rooms.createRoom().roomId().value();
        List<List<JsonNode>> received=List.of(new CopyOnWriteArrayList<>(),new CopyOnWriteArrayList<>());
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            List<Future<?>> tasks=new ArrayList<>();
            for(int index=0;index<2;index++) {
                final int number=index;
                WebSocketSession session=mock(WebSocketSession.class);
                when(session.getId()).thenReturn("audit-"+index);when(session.isOpen()).thenReturn(true);
                doAnswer(invocation->{received.get(number).add(mapper.readTree(((TextMessage)invocation.getArgument(0)).getPayload()));return null;})
                        .when(session).sendMessage(any());
                handler.afterConnectionEstablished(session);
                String token=capabilities.issue(room,UUID.randomUUID(),MeetingRole.PARTICIPANT).token();
                String join=mapper.writeValueAsString(Map.of("version",1,"type","JOIN","roomId",room,"capability",token));
                tasks.add(pool.submit(()->handler.handleTextMessage(session,new TextMessage(join))));
            }
            for(var task:tasks)task.get(8,TimeUnit.SECONDS);
        } finally {pool.shutdownNow();}
        for(var messages:received) {
            var joined=messages.stream().filter(m->m.path("type").asText().equals("JOINED")).findFirst().orElseThrow();
            boolean answerer=!joined.path("payload").path("peers").get(0).path("initiator").asBoolean();
            boolean toldToOffer=messages.stream().anyMatch(m->m.path("type").asText().equals("PARTICIPANT_JOINED")
                    &&m.path("payload").path("initiator").asBoolean());
            assertThat(answerer&&toldToOffer).as("Answerer received contradictory initiator=true notification: %s",messages).isFalse();
        }
    }
}
