package com.adventacademy.meetingservice.application.security;

import com.adventacademy.meetingservice.domain.exception.InvalidCapabilityException;
import com.adventacademy.meetingservice.domain.model.MeetingRole;
import com.adventacademy.meetingservice.infrastructure.config.MeetingProperties;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

@Service
public class MeetingCapabilityService {

    private static final int TOKEN_BYTES = 32;

    private final Map<String, StoredCapability> capabilities = new HashMap<>();
    private final SecureRandom secureRandom;
    private final Clock clock;
    private final long ttlSeconds;

    @Autowired
    public MeetingCapabilityService(Clock clock, MeetingProperties properties) {
        this(clock, properties.getCapabilityTtlSeconds(), new SecureRandom());
    }

    MeetingCapabilityService(Clock clock, long ttlSeconds, SecureRandom secureRandom) {
        this.clock = clock;
        this.ttlSeconds = ttlSeconds;
        this.secureRandom = secureRandom;
    }

    public synchronized IssuedCapability issue(String roomId, UUID userId, MeetingRole role) {
        removeExpired();
        final byte[] tokenBytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(tokenBytes);
        final String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        final Instant expiresAt = clock.instant().plusSeconds(ttlSeconds);
        capabilities.put(hash(token), new StoredCapability(roomId, userId, role, expiresAt));
        return new IssuedCapability(token, expiresAt, role);
    }

    public synchronized MeetingGrant consume(String token, String requestedRoomId) {
        if (token == null || token.isBlank()) {
            throw invalid();
        }
        removeExpired();
        final StoredCapability stored = capabilities.remove(hash(token));
        if (stored == null || !stored.roomId().equals(requestedRoomId)) {
            throw invalid();
        }
        return new MeetingGrant(stored.roomId(), stored.userId(), stored.role());
    }

    public synchronized void revoke(String roomId, UUID userId) {
        final Iterator<StoredCapability> iterator = capabilities.values().iterator();
        while (iterator.hasNext()) {
            final StoredCapability capability = iterator.next();
            if (capability.roomId().equals(roomId) && capability.userId().equals(userId)) {
                iterator.remove();
            }
        }
    }

    public synchronized void revokeRoom(String roomId) {
        capabilities.values().removeIf(capability -> capability.roomId().equals(roomId));
    }

    synchronized int activeCapabilityCount() {
        removeExpired();
        return capabilities.size();
    }

    private void removeExpired() {
        final Instant now = clock.instant();
        capabilities.values().removeIf(capability -> !capability.expiresAt().isAfter(now));
    }

    private String hash(String token) {
        try {
            final byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private InvalidCapabilityException invalid() {
        return new InvalidCapabilityException(
                "INVALID_CAPABILITY",
                "The signaling capability is invalid, expired, revoked, already used, or scoped to another room");
    }

    private record StoredCapability(
            String roomId,
            UUID userId,
            MeetingRole role,
            Instant expiresAt) {
    }
}
