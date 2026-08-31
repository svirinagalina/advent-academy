package com.adventacademy.userservice.infrastructure.persistence.jpa;

import com.adventacademy.userservice.infrastructure.persistence.entity.RecoveryCodeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RecoveryCodeJpaRepository extends JpaRepository<RecoveryCodeEntity, UUID> {

    /**
     * Find all recovery codes for a user (both used and unused).
     */
    List<RecoveryCodeEntity> findByUserId(UUID userId);

    /**
     * Find all unused recovery codes for a user.
     */
    @Query("SELECT rc FROM RecoveryCodeEntity rc WHERE rc.user.id = :userId AND rc.usedAt IS NULL ORDER BY rc.createdAt ASC")
    List<RecoveryCodeEntity> findUnusedByUserId(@Param("userId") UUID userId);

    /**
     * Find recovery code by hash (for validation during login).
     */
    Optional<RecoveryCodeEntity> findByCodeHash(String codeHash);

    /**
     * Count unused recovery codes for a user.
     */
    @Query("SELECT COUNT(rc) FROM RecoveryCodeEntity rc WHERE rc.user.id = :userId AND rc.usedAt IS NULL")
    long countUnusedByUserId(@Param("userId") UUID userId);
}
