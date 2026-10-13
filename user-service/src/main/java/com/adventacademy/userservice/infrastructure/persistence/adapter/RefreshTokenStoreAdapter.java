package com.adventacademy.userservice.infrastructure.persistence.adapter;

import com.adventacademy.userservice.application.port.RefreshTokenStore;
import com.adventacademy.userservice.infrastructure.persistence.entity.RefreshTokenEntity;
import com.adventacademy.userservice.infrastructure.persistence.jpa.RefreshTokenJpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Repository
public class RefreshTokenStoreAdapter implements RefreshTokenStore {

    private final RefreshTokenJpaRepository tokens;
    private final Clock clock;

    public RefreshTokenStoreAdapter(RefreshTokenJpaRepository tokens, Clock clock) {
        this.tokens = tokens;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void save(String jtiDigest, UUID userId, Instant expiresAt) {
        tokens.deleteExpiredByUserId(userId, clock.instant());
        tokens.save(new RefreshTokenEntity(jtiDigest, userId, expiresAt));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isActive(String jtiDigest, Instant now) {
        return tokens.isActive(jtiDigest, now);
    }

    @Override
    @Transactional
    public boolean consume(String jtiDigest, Instant now) {
        return tokens.consume(jtiDigest, now) == 1;
    }

    @Override
    @Transactional
    public void revokeByUserId(UUID userId) {
        tokens.revokeByUserId(userId);
    }
}
