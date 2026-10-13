package com.adventacademy.userservice.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * One-time backup recovery code for MFA.
 * When a user enables MFA, we generate 8 recovery codes.
 * Each code can be used exactly once to regain access if the authenticator is lost.
 */
public final class RecoveryCode {
    private final UUID id;
    private final UUID userId;
    private final String codeHash;    // SHA-256 digest of the recovery code
    private final Instant createdAt;
    private Instant usedAt;           // null = unused, set when code is consumed

    private RecoveryCode(UUID id, UUID userId, String codeHash, Instant createdAt, Instant usedAt) {
        this.id = id;
        this.userId = userId;
        this.codeHash = codeHash;
        this.createdAt = createdAt;
        this.usedAt = usedAt;
    }

    /**
     * Create a new unused recovery code with SHA-256 hash.
     * The plaintext code is NOT stored, only the hash.
     *
     * @param userId User who owns this recovery code
     * @param codeHash SHA-256 digest of the 8-character recovery code
     * @param now Current timestamp
     */
    public static RecoveryCode create(UUID userId, String codeHash, Instant now) {
        if (userId == null) throw new IllegalArgumentException("userId must not be null");
        if (codeHash == null || codeHash.isBlank()) throw new IllegalArgumentException("codeHash must not be blank");
        if (now == null) throw new IllegalArgumentException("now must not be null");

        return new RecoveryCode(UUID.randomUUID(), userId, codeHash, now, null);
    }

    /**
     * Restore a recovery code from persistence layer.
     */
    public static RecoveryCode restore(UUID id, UUID userId, String codeHash, Instant createdAt, Instant usedAt) {
        if (id == null) throw new IllegalArgumentException("id must not be null");
        if (userId == null) throw new IllegalArgumentException("userId must not be null");
        if (codeHash == null || codeHash.isBlank()) throw new IllegalArgumentException("codeHash must not be blank");
        if (createdAt == null) throw new IllegalArgumentException("createdAt must not be null");

        return new RecoveryCode(id, userId, codeHash, createdAt, usedAt);
    }

    /**
     * Mark this recovery code as used (one-time use enforcement).
     */
    public void markAsUsed(Instant now) {
        if (now == null) throw new IllegalArgumentException("now must not be null");
        if (this.usedAt != null) throw new IllegalStateException("Recovery code already used at " + this.usedAt);
        this.usedAt = now;
    }

    /**
     * Check if this code has been used.
     */
    public boolean isUsed() {
        return this.usedAt != null;
    }

    /**
     * Check if this code is still available (unused).
     */
    public boolean isAvailable() {
        return this.usedAt == null;
    }

    // Getters
    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }
}
