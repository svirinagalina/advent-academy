package com.adventacademy.userservice.domain.repository;

import com.adventacademy.userservice.domain.model.RefreshSession;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefreshSessionRepository {

    void save(RefreshSession session);

    Optional<RefreshSession> findByJtiDigest(String jtiDigest);

    List<RefreshSession> findActiveByUserId(UUID userId);

    List<RefreshSession> findByTokenFamilyId(UUID tokenFamilyId);

    List<RefreshSession> findExpiredSessions(Instant now);

    List<RefreshSession> findAllByUserId(UUID userId);

    void deleteById(UUID id);

    void delete(RefreshSession session);
}
