package com.adventacademy.meetingservice.adapter.rest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static com.adventacademy.meetingservice.support.TestAccessTokens.accessToken;
import static com.adventacademy.meetingservice.support.TestAccessTokens.expiredAccessToken;
import static com.adventacademy.meetingservice.support.TestAccessTokens.refreshToken;

import java.util.UUID;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class MeetingControllerIT {

    @Autowired
    private MockMvc mockMvc;

    private final UUID hostId = UUID.randomUUID();

    @Test
    void createsRoomWithStableExternalContract() throws Exception {
        mockMvc.perform(post("/api/meetings/rooms")
                        .header(HttpHeaders.AUTHORIZATION, bearer(hostId))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern("/api/meetings/rooms/[a-zA-Z0-9_-]+")))
                .andExpect(jsonPath("$.roomId", matchesPattern("[a-zA-Z0-9_-]{8,64}")))
                .andExpect(jsonPath("$.status").value("WAITING"))
                .andExpect(jsonPath("$.maxParticipants").value(2))
                .andExpect(jsonPath("$.participantCount").value(0))
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    void unknownRoomReturnsConsistentNotFoundError() throws Exception {
        mockMvc.perform(get("/api/meetings/rooms/room-missing")
                        .header(HttpHeaders.AUTHORIZATION, bearer(hostId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ROOM_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Meeting room not found: room-missing"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void invalidRoomIdentifierReturnsValidationError() throws Exception {
        mockMvc.perform(get("/api/meetings/rooms/bad!")
                        .header(HttpHeaders.AUTHORIZATION, bearer(hostId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void returnsExternalizedIceConfiguration() throws Exception {
        mockMvc.perform(get("/api/meetings/config")
                        .header(HttpHeaders.AUTHORIZATION, bearer(hostId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.iceServers").isArray())
                .andExpect(jsonPath("$.iceServers").isEmpty());
    }

    @Test
    void rejectsAnonymousAndCrossUserAccessButAllowsHostGrantedMember() throws Exception {
        final String roomId = createRoom(hostId);
        final UUID memberId = UUID.randomUUID();
        final UUID strangerId = UUID.randomUUID();

        mockMvc.perform(get("/api/meetings/rooms/{roomId}", roomId))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/meetings/rooms/{roomId}", roomId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(strangerId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MEETING_ACCESS_DENIED"));
        mockMvc.perform(put("/api/meetings/rooms/{roomId}/members/{memberId}", roomId, memberId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(hostId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("PARTICIPANT"));
        mockMvc.perform(post("/api/meetings/rooms/{roomId}/capabilities", roomId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capability", matchesPattern("[A-Za-z0-9_-]{43}")))
                .andExpect(jsonPath("$.expiresAt").exists())
                .andExpect(jsonPath("$.role").value("PARTICIPANT"));
    }

    @Test
    void rejectsExpiredAndRefreshTokensAtAccessTokenBoundary() throws Exception {
        final UUID userId = UUID.randomUUID();

        mockMvc.perform(post("/api/meetings/rooms")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredAccessToken(userId)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/meetings/rooms")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + refreshToken(userId)))
                .andExpect(status().isUnauthorized());
    }

    private String createRoom(UUID userId) throws Exception {
        return mockMvc.perform(post("/api/meetings/rooms")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userId)))
                .andReturn().getResponse().getContentAsString()
                .replaceAll(".*\\\"roomId\\\":\\\"([^\\\"]+)\\\".*", "$1");
    }

    private String bearer(UUID userId) {
        return "Bearer " + accessToken(userId);
    }
}
