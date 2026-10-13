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

    protected RefreshTokenEntity() {
    }

    public RefreshTokenEntity(String jtiDigest, UUID userId, Instant expiresAt) {
        this.jtiDigest = jtiDigest;
        this.userId = userId;
        this.expiresAt = expiresAt;
    }
}
