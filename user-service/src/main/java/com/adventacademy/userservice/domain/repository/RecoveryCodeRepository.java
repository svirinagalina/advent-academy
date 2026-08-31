package com.adventacademy.userservice.domain.repository;

import com.adventacademy.userservice.domain.model.RecoveryCode;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecoveryCodeRepository {
    RecoveryCode save(RecoveryCode code);

    List<RecoveryCode> saveAll(List<RecoveryCode> codes);

    Optional<RecoveryCode> findById(UUID id);

    Optional<RecoveryCode> findByCodeHash(String codeHash);

    List<RecoveryCode> findByUserId(UUID userId);

    List<RecoveryCode> findUnusedByUserId(UUID userId);

    long countUnusedByUserId(UUID userId);

    void deleteById(UUID id);

    void deleteAllByUserId(UUID userId);
}
