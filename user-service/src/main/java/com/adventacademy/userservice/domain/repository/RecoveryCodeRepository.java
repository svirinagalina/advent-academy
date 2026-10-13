package com.adventacademy.userservice.domain.repository;

import com.adventacademy.userservice.domain.model.RecoveryCode;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecoveryCodeRepository {

    RecoveryCode save(RecoveryCode code);

    List<RecoveryCode> saveAll(List<RecoveryCode> codes);

    Optional<RecoveryCode> findById(UUID id);

    List<RecoveryCode> findUnusedByUserId(UUID userId);

    long countUnusedByUserId(UUID userId);

    /**
     * Burns the user's unused code with the given hash. Exactly one of any number of concurrent
     * callers gets {@code true}; the code is never accepted twice.
     */
    boolean consume(UUID userId, String codeHash, Instant usedAt);

    void deleteAllByUserId(UUID userId);
}
