package com.adventacademy.userservice.infrastructure.persistence.jpa;

import com.adventacademy.userservice.domain.model.RefreshSession;
import com.adventacademy.userservice.infrastructure.persistence.entity.RefreshSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshSessionJpaRepository extends JpaRepository<RefreshSessionEntity, UUID> {

    Optional<RefreshSessionEntity> findByJtiDigest(String jtiDigest);

    @Query("SELECT e FROM RefreshSessionEntity e WHERE e.userId = :userId AND e.status = 'ACTIVE'")
    List<RefreshSessionEntity> findActiveByUserId(UUID userId);

    @Query("SELECT e FROM RefreshSessionEntity e WHERE e.tokenFamilyId = :tokenFamilyId")
    List<RefreshSessionEntity> findByTokenFamilyId(UUID tokenFamilyId);

    @Query("SELECT e FROM RefreshSessionEntity e WHERE e.expiresAt < :now AND e.status = 'ACTIVE'")
    List<RefreshSessionEntity> findExpiredSessions(Instant now);

    @Query("SELECT e FROM RefreshSessionEntity e WHERE e.userId = :userId AND e.status IN ('ACTIVE', 'REVOKED')")
    List<RefreshSessionEntity> findAllByUserId(UUID userId);
}
