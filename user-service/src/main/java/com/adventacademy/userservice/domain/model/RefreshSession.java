package com.adventacademy.userservice.domain.model;

import java.time.Instant;
import java.util.UUID;

public class RefreshSession {

    public enum Status {
        ACTIVE,
        REVOKED,
        EXPIRED
    }

    private UUID id;
    private UUID userId;
    private UUID tokenFamilyId;
    private String jtiDigest;
    private String deviceId;
    private String deviceName;
    private String ipAddress;
    private String userAgent;
    private Instant createdAt;
    private Instant rotatedAt;
    private Instant expiresAt;
    private Instant lastActivityAt;
    private Instant revokedAt;
    private Status status;

    public RefreshSession(UUID id, UUID userId, UUID tokenFamilyId, String jtiDigest,
                         String deviceId, String deviceName, String ipAddress, String userAgent,
                         Instant createdAt, Instant rotatedAt, Instant expiresAt,
                         Instant lastActivityAt, Instant revokedAt, Status status) {
        this.id = id;
        this.userId = userId;
        this.tokenFamilyId = tokenFamilyId;
        this.jtiDigest = jtiDigest;
        this.deviceId = deviceId;
        this.deviceName = deviceName;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.createdAt = createdAt;
        this.rotatedAt = rotatedAt;
        this.expiresAt = expiresAt;
        this.lastActivityAt = lastActivityAt;
        this.revokedAt = revokedAt;
        this.status = status;
    }

    public static RefreshSession create(UUID userId, UUID tokenFamilyId, String jtiDigest,
                                       String deviceId, String deviceName, String ipAddress,
                                       String userAgent, Instant expiresAt) {
        Instant now = Instant.now();
        return new RefreshSession(
                UUID.randomUUID(),
                userId,
                tokenFamilyId,
                jtiDigest,
                deviceId,
                deviceName,
                ipAddress,
                userAgent,
                now,
                now,
                expiresAt,
                now,
                null,
                Status.ACTIVE
        );
    }

    public void rotate(String newJtiDigest, Instant newExpiresAt) {
        this.jtiDigest = newJtiDigest;
        this.rotatedAt = Instant.now();
        this.lastActivityAt = Instant.now();
        // Keep same tokenFamilyId and expiresAt
    }

    public void markAsUsed() {
        this.lastActivityAt = Instant.now();
    }

    public void revoke() {
        this.status = Status.REVOKED;
        this.revokedAt = Instant.now();
    }

    public boolean isActive(Instant now) {
        return status == Status.ACTIVE && now.isBefore(expiresAt);
    }

    public boolean isIdleExpired(Instant now, long idleTimeoutSeconds) {
        return lastActivityAt.plusSeconds(idleTimeoutSeconds).isBefore(now);
    }

    public boolean isAbsoluteExpired(Instant now) {
        return now.isAfter(expiresAt);
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getTokenFamilyId() {
        return tokenFamilyId;
    }

    public String getJtiDigest() {
        return jtiDigest;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getRotatedAt() {
        return rotatedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getLastActivityAt() {
        return lastActivityAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public Status getStatus() {
        return status;
    }
}
