package com.adventacademy.userservice.infrastructure.persistence.jpa;

import com.adventacademy.userservice.infrastructure.persistence.entity.VerificationTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface VerificationTokenJpaRepository extends JpaRepository<VerificationTokenEntity, UUID> {

    /**
     * Find token by digest (for validation).
     */
    Optional<VerificationTokenEntity> findByTokenDigest(String tokenDigest);

    /**
     * Find active (unused) token for user and purpose.
     */
    @Query("SELECT vt FROM VerificationTokenEntity vt WHERE vt.user.id = :userId AND vt.purpose = :purpose AND vt.usedAt IS NULL")
    Optional<VerificationTokenEntity> findActiveByUserIdAndPurpose(@Param("userId") UUID userId, @Param("purpose") String purpose);

    /**
     * Delete expired tokens (cleanup).
     */
    @Query("DELETE FROM VerificationTokenEntity vt WHERE vt.expiresAt < CURRENT_TIMESTAMP")
    void deleteExpired();
}
