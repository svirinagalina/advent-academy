package com.adventacademy.userservice.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Secure, one-time, expiring token for email verification and password reset flows.
 * Token is created with:
 * - Cryptographically random bytes
 * - SHA-256 digest stored in DB (never plaintext)
 * - Configurable TTL (24 hours for email verification, 1 hour for password reset)
 * - Purpose field to prevent using email-verification token as password-reset
 * - One-time use: once consumed, token cannot be reused
 */
public final class VerificationToken {

    public static final String PURPOSE_EMAIL_VERIFICATION = "email_verification";
    public static final String PURPOSE_PASSWORD_RESET = "password_reset";

    private final UUID id;
    private final UUID userId;
    private final String tokenDigest;        // SHA-256 hash of token (plaintext never stored)
    private final String purpose;             // "email_verification" or "password_reset"
    private final Instant createdAt;
    private final Instant expiresAt;
    private Instant usedAt;                   // null = unused, set when token is consumed

    private VerificationToken(UUID id, UUID userId, String tokenDigest, String purpose,
                             Instant createdAt, Instant expiresAt, Instant usedAt) {
        this.id = id;
        this.userId = userId;
        this.tokenDigest = tokenDigest;
        this.purpose = purpose;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.usedAt = usedAt;
    }

    /**
     * Create a new verification token with SHA-256 digest.
     * Plaintext token is NOT stored, only the hash.
     *
     * @param userId User who this token is for
     * @param tokenDigest SHA-256 digest of random 32-byte token (hex encoded)
     * @param purpose Purpose of token: "email_verification" or "password_reset"
     * @param createdAt Current timestamp
     * @param expiresAt Expiration timestamp (TTL applied here)
     */
    public static VerificationToken create(UUID userId, String tokenDigest, String purpose,
                                          Instant createdAt, Instant expiresAt) {
        if (userId == null) throw new IllegalArgumentException("userId must not be null");
        if (tokenDigest == null || tokenDigest.isBlank()) throw new IllegalArgumentException("tokenDigest must not be blank");
        if (purpose == null || purpose.isBlank()) throw new IllegalArgumentException("purpose must not be blank");
        if (createdAt == null) throw new IllegalArgumentException("createdAt must not be null");
        if (expiresAt == null) throw new IllegalArgumentException("expiresAt must not be null");
        if (!expiresAt.isAfter(createdAt)) throw new IllegalArgumentException("expiresAt must be after createdAt");

        return new VerificationToken(UUID.randomUUID(), userId, tokenDigest, purpose, createdAt, expiresAt, null);
    }

    /**
     * Restore a verification token from persistence.
     */
    public static VerificationToken restore(UUID id, UUID userId, String tokenDigest, String purpose,
                                           Instant createdAt, Instant expiresAt, Instant usedAt) {
        if (id == null) throw new IllegalArgumentException("id must not be null");
        if (userId == null) throw new IllegalArgumentException("userId must not be null");
        if (tokenDigest == null || tokenDigest.isBlank()) throw new IllegalArgumentException("tokenDigest must not be blank");
        if (purpose == null || purpose.isBlank()) throw new IllegalArgumentException("purpose must not be blank");
        if (createdAt == null) throw new IllegalArgumentException("createdAt must not be null");
        if (expiresAt == null) throw new IllegalArgumentException("expiresAt must not be null");

        return new VerificationToken(id, userId, tokenDigest, purpose, createdAt, expiresAt, usedAt);
    }

    /**
     * Validate token:
     * - Not yet expired
     * - Not already used
     * - Purpose matches expected usage
     *
     * @param now Current timestamp for expiry check
     * @param expectedPurpose Expected token purpose (prevents using wrong token type)
     * @return true if token is valid, false otherwise
     */
    public boolean isValid(Instant now, String expectedPurpose) {
        if (now == null) throw new IllegalArgumentException("now must not be null");
        if (expectedPurpose == null) throw new IllegalArgumentException("expectedPurpose must not be null");

        // Check expiry
        if (now.isAfter(expiresAt)) {
            return false;  // Expired
        }

        // Check one-time use
        if (this.usedAt != null) {
            return false;  // Already used
        }

        // Check purpose matches
        if (!this.purpose.equals(expectedPurpose)) {
            return false;  // Wrong purpose (e.g., using email token for password reset)
        }

        return true;
    }

    /**
     * Check if token is expired.
     */
    public boolean isExpired(Instant now) {
        return now.isAfter(expiresAt);
    }

    /**
     * Check if token has been used.
     */
    public boolean isUsed() {
        return this.usedAt != null;
    }

    /**
     * Mark token as used (one-time use enforcement).
     */
    public void markAsUsed(Instant now) {
        if (now == null) throw new IllegalArgumentException("now must not be null");
        if (this.usedAt != null) throw new IllegalStateException("Token already used at " + this.usedAt);
        this.usedAt = now;
    }

    // Getters
    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getTokenDigest() {
        return tokenDigest;
    }

    public String getPurpose() {
        return purpose;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }
}
