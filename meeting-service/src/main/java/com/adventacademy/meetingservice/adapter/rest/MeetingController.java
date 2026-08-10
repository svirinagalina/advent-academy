package com.adventacademy.meetingservice.adapter.rest;

import com.adventacademy.meetingservice.adapter.rest.dto.MeetingConfigurationResponse;
import com.adventacademy.meetingservice.adapter.rest.dto.RoomResponse;
import com.adventacademy.meetingservice.application.service.RoomService;
import com.adventacademy.meetingservice.infrastructure.config.MeetingProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/meetings")
public class MeetingController {

    private final RoomService roomService;
    private final MeetingProperties meetingProperties;

    public MeetingController(RoomService roomService, MeetingProperties meetingProperties) {
        this.roomService = roomService;
        this.meetingProperties = meetingProperties;
    }

    @PostMapping("/rooms")
    public ResponseEntity<RoomResponse> createRoom() {
        final RoomResponse response = RoomResponse.from(roomService.createRoom());
        final URI location = URI.create("/api/meetings/rooms/" + response.roomId());
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/rooms/{roomId}")
    public RoomResponse getRoom(@PathVariable String roomId) {
        return RoomResponse.from(roomService.getRoom(roomId));
    }

    @GetMapping("/config")
    public MeetingConfigurationResponse getConfiguration() {
        return MeetingConfigurationResponse.from(meetingProperties);
    }
}
