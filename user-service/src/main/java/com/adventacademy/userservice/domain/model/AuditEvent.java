package com.adventacademy.userservice.domain.model;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

public class AuditEvent {

    private final UUID id;
    private final AuditEventType eventType;
    private final UUID actorUserId;
    private final UUID targetUserId;
    private final LocalDateTime timestamp;
    private final String ipAddress;
    private final String userAgent;
    private final AuditResult result;
    private final String reason;
    private final Map<String, String> metadata;

    public AuditEvent(
            UUID id,
            AuditEventType eventType,
            UUID actorUserId,
            UUID targetUserId,
            LocalDateTime timestamp,
            String ipAddress,
            String userAgent,
            AuditResult result,
            String reason,
            Map<String, String> metadata) {
        this.id = id;
        this.eventType = eventType;
        this.actorUserId = actorUserId;
        this.targetUserId = targetUserId;
        this.timestamp = timestamp;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.result = result;
        this.reason = reason;
        this.metadata = metadata;
    }

    public UUID getId() { return id; }
    public AuditEventType getEventType() { return eventType; }
    public UUID getActorUserId() { return actorUserId; }
    public UUID getTargetUserId() { return targetUserId; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public String getIpAddress() { return ipAddress; }
    public String getUserAgent() { return userAgent; }
    public AuditResult getResult() { return result; }
    public String getReason() { return reason; }
    public Map<String, String> getMetadata() { return metadata; }
}