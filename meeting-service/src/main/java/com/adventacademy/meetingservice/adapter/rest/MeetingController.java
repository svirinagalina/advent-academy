package com.adventacademy.meetingservice.adapter.rest;

import com.adventacademy.meetingservice.adapter.rest.dto.MeetingConfigurationResponse;
import com.adventacademy.meetingservice.adapter.rest.dto.CapabilityResponse;
import com.adventacademy.meetingservice.adapter.rest.dto.MemberResponse;
import com.adventacademy.meetingservice.adapter.rest.dto.RoomResponse;
import com.adventacademy.meetingservice.application.security.MeetingAccessService;
import com.adventacademy.meetingservice.adapter.websocket.MeetingSignalingHandler;
import com.adventacademy.meetingservice.infrastructure.config.MeetingProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/meetings")
public class MeetingController {

    private final MeetingAccessService meetingAccessService;
    private final MeetingProperties meetingProperties;
    private final MeetingSignalingHandler signalingHandler;

    public MeetingController(
            MeetingAccessService meetingAccessService,
            MeetingProperties meetingProperties,
            MeetingSignalingHandler signalingHandler) {
        this.meetingAccessService = meetingAccessService;
        this.meetingProperties = meetingProperties;
        this.signalingHandler = signalingHandler;
    }

    @PostMapping("/rooms")
    public ResponseEntity<RoomResponse> createRoom(Authentication authentication) {
        final RoomResponse response = RoomResponse.from(
                meetingAccessService.createPrivateMeeting(userId(authentication)));
        final URI location = URI.create("/api/meetings/rooms/" + response.roomId());
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/rooms/{roomId}")
    public RoomResponse getRoom(@PathVariable String roomId, Authentication authentication) {
        return RoomResponse.from(meetingAccessService.getAuthorizedRoom(roomId, userId(authentication)));
    }

    @PutMapping("/rooms/{roomId}/members/{memberId}")
    public MemberResponse addMember(
            @PathVariable String roomId,
            @PathVariable UUID memberId,
            Authentication authentication) {
        return new MemberResponse(
                meetingAccessService.addMember(roomId, userId(authentication), memberId).name());
    }

    @DeleteMapping("/rooms/{roomId}/members/{memberId}")
    public ResponseEntity<Void> removeMember(
            @PathVariable String roomId,
            @PathVariable UUID memberId,
            Authentication authentication) {
        meetingAccessService.removeMember(roomId, userId(authentication), memberId);
        signalingHandler.revoke(roomId, memberId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/rooms/{roomId}/capabilities")
    public CapabilityResponse issueCapability(
            @org.springframework.web.bind.annotation.RequestHeader("Authorization") String authorization,
            @PathVariable String roomId,
            Authentication authentication) {
        return CapabilityResponse.from(
                meetingAccessService.issueCapability(roomId, userId(authentication), authorization));
    }

    @GetMapping("/config")
    public MeetingConfigurationResponse getConfiguration() {
        return MeetingConfigurationResponse.from(meetingProperties);
    }

    private UUID userId(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }
}
