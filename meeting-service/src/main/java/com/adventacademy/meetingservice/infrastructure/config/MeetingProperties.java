package com.adventacademy.meetingservice.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "meeting")
public class MeetingProperties {

    private List<String> allowedOrigins = new ArrayList<>();
    private int maxMessageBytes = 65_536;
    private List<IceServer> iceServers = new ArrayList<>();

    public List<String> getAllowedOrigins() {
        return List.copyOf(allowedOrigins);
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = new ArrayList<>(allowedOrigins);
    }

    public int getMaxMessageBytes() {
        return maxMessageBytes;
    }

    public void setMaxMessageBytes(int maxMessageBytes) {
        this.maxMessageBytes = maxMessageBytes;
    }

    public List<IceServer> getIceServers() {
        return List.copyOf(iceServers);
    }

    public void setIceServers(List<IceServer> iceServers) {
        this.iceServers = new ArrayList<>(iceServers);
    }

    public static class IceServer {

        private String urls;
        private String username;
        private String credential;

        public String getUrls() {
            return urls;
        }

        public void setUrls(String urls) {
            this.urls = urls;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getCredential() {
            return credential;
        }

        public void setCredential(String credential) {
            this.credential = credential;
        }
    }
}
