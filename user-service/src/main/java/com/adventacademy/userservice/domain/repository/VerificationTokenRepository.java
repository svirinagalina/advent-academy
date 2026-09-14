package com.adventacademy.userservice.domain.repository;

import com.adventacademy.userservice.domain.model.VerificationToken;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface VerificationTokenRepository {

    VerificationToken save(VerificationToken token);

    Optional<VerificationToken> findById(UUID id);

    Optional<VerificationToken> findByTokenDigest(String digest);

    Optional<VerificationToken> findActiveByUserIdAndPurpose(UUID userId, String purpose);

    /**
     * Marks the token used if - and only if - nobody else did so first. Two concurrent callers
     * presenting the same token therefore see exactly one {@code true}.
     */
    boolean consume(UUID tokenId, Instant usedAt);

    /** Invalidates every still-active token of the user for the purpose, e.g. before a fresh one is issued. */
    int invalidateActive(UUID userId, String purpose, Instant usedAt);

    void deleteExpired();
}
