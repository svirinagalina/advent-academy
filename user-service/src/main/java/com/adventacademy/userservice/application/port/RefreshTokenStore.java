package com.adventacademy.userservice.application.port;

import java.time.Instant;
import java.util.UUID;

/** Shared durable state for single-use refresh tokens. Only a JTI digest is stored. */
public interface RefreshTokenStore {
    void save(String jtiDigest, UUID userId, Instant expiresAt);
    void save(String jtiDigest, UUID userId, Instant expiresAt, UUID familyId);
    UUID family(String jtiDigest);
    boolean revokeConsumedFamily(String jtiDigest, Instant now);
    boolean isActive(String jtiDigest, Instant now);
    boolean consume(String jtiDigest, Instant now);
    void revokeByUserId(UUID userId);
}
