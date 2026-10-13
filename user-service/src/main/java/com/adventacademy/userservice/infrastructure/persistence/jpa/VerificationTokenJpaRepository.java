package com.adventacademy.userservice.infrastructure.persistence.jpa;

import com.adventacademy.userservice.infrastructure.persistence.entity.VerificationTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VerificationTokenJpaRepository extends JpaRepository<VerificationTokenEntity, UUID> {

    Optional<VerificationTokenEntity> findByTokenDigest(String tokenDigest);

    @Query("SELECT vt FROM VerificationTokenEntity vt WHERE vt.user.id = :userId AND vt.purpose = :purpose AND vt.usedAt IS NULL")
    Optional<VerificationTokenEntity> findActiveByUserIdAndPurpose(@Param("userId") UUID userId, @Param("purpose") String purpose);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE VerificationTokenEntity vt SET vt.usedAt = :usedAt WHERE vt.id = :id AND vt.usedAt IS NULL")
    int markUsed(@Param("id") UUID id, @Param("usedAt") Instant usedAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE VerificationTokenEntity vt SET vt.usedAt = :usedAt WHERE vt.user.id = :userId AND vt.purpose = :purpose AND vt.usedAt IS NULL")
    int invalidateActive(@Param("userId") UUID userId, @Param("purpose") String purpose, @Param("usedAt") Instant usedAt);

    @Modifying
    @Query("DELETE FROM VerificationTokenEntity vt WHERE vt.expiresAt < CURRENT_TIMESTAMP")
    void deleteExpired();
}
