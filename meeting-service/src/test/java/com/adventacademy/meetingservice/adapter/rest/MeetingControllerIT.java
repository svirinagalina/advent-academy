package com.adventacademy.meetingservice.adapter.rest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class MeetingControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createsRoomWithStableExternalContract() throws Exception {
        mockMvc.perform(post("/api/meetings/rooms").accept(MediaType.APPLICATION_JSON))
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
        mockMvc.perform(get("/api/meetings/rooms/room-missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ROOM_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Meeting room not found: room-missing"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void invalidRoomIdentifierReturnsValidationError() throws Exception {
        mockMvc.perform(get("/api/meetings/rooms/bad!"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void returnsExternalizedIceConfiguration() throws Exception {
        mockMvc.perform(get("/api/meetings/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.iceServers").isArray())
                .andExpect(jsonPath("$.iceServers").isEmpty());
    }
}
