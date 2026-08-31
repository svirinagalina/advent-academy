package com.adventacademy.userservice.domain.repository;

import com.adventacademy.userservice.domain.model.VerificationToken;

import java.util.Optional;
import java.util.UUID;

public interface VerificationTokenRepository {
    VerificationToken save(VerificationToken token);
    Optional<VerificationToken> findById(UUID id);
    Optional<VerificationToken> findByTokenDigest(String digest);
    Optional<VerificationToken> findActiveByUserIdAndPurpose(UUID userId, String purpose);
    void deleteExpired();
}
