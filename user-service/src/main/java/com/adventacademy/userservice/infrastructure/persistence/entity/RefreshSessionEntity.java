package com.adventacademy.userservice.infrastructure.persistence.entity;

import com.adventacademy.userservice.domain.model.RefreshSession;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_sessions", indexes = {
        @Index(name = "idx_user_id_status", columnList = "user_id,status"),
        @Index(name = "idx_jti_digest", columnList = "jti_digest", unique = true),
        @Index(name = "idx_token_family_id", columnList = "token_family_id"),
        @Index(name = "idx_expires_at", columnList = "expires_at")
})
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshSessionEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private UUID tokenFamilyId;

    @Column(nullable = false, unique = true, length = 64)
    private String jtiDigest;

    @Column(length = 255)
    private String deviceId;

    @Column(length = 500)
    private String deviceName;

    @Column(length = 45)
    private String ipAddress;

    @Column(columnDefinition = "TEXT")
    private String userAgent;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant rotatedAt;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private Instant lastActivityAt;

    private Instant revokedAt;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private RefreshSession.Status status;

    public static RefreshSessionEntity from(RefreshSession domain) {
        return RefreshSessionEntity.builder()
                .id(domain.getId())
                .userId(domain.getUserId())
                .tokenFamilyId(domain.getTokenFamilyId())
                .jtiDigest(domain.getJtiDigest())
                .deviceId(domain.getDeviceId())
                .deviceName(domain.getDeviceName())
                .ipAddress(domain.getIpAddress())
                .userAgent(domain.getUserAgent())
                .createdAt(domain.getCreatedAt())
                .rotatedAt(domain.getRotatedAt())
                .expiresAt(domain.getExpiresAt())
                .lastActivityAt(domain.getLastActivityAt())
                .revokedAt(domain.getRevokedAt())
                .status(domain.getStatus())
                .build();
    }

    public RefreshSession toDomain() {
        return new RefreshSession(
                id, userId, tokenFamilyId, jtiDigest,
                deviceId, deviceName, ipAddress, userAgent,
                createdAt, rotatedAt, expiresAt, lastActivityAt,
                revokedAt, status
        );
    }
}
