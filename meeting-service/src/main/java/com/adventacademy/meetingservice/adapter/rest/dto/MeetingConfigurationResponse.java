package com.adventacademy.meetingservice.adapter.rest.dto;

import com.adventacademy.meetingservice.infrastructure.config.MeetingProperties;

import java.util.List;

public record MeetingConfigurationResponse(List<IceServerResponse> iceServers) {

    public static MeetingConfigurationResponse from(MeetingProperties properties) {
        final List<IceServerResponse> servers = properties.getIceServers().stream()
                .map(server -> new IceServerResponse(server.getUrls(), server.getUsername(), server.getCredential()))
                .toList();
        return new MeetingConfigurationResponse(servers);
    }

    public record IceServerResponse(String urls, String username, String credential) {
    }
}
