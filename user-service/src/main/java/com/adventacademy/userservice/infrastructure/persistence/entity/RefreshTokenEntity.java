package com.adventacademy.userservice.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
public class RefreshTokenEntity {

    @Id
    @Column(name = "jti_digest", length = 64, nullable = false)
    private String jtiDigest;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @Column(nullable = false)
    private boolean consumed;

    public UUID getFamilyId() { return familyId; }
    public boolean isConsumed() { return consumed; }

    protected RefreshTokenEntity() {
    }

    public RefreshTokenEntity(String jtiDigest, UUID userId, Instant expiresAt) {
        this(jtiDigest, userId, expiresAt, UUID.randomUUID());
    }

    public RefreshTokenEntity(String jtiDigest, UUID userId, Instant expiresAt, UUID familyId) {
        this.familyId = familyId;
        this.jtiDigest = jtiDigest;
        this.userId = userId;
        this.expiresAt = expiresAt;
    }
}
