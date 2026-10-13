package com.adventacademy.userservice.infrastructure.persistence.jpa;

import com.adventacademy.userservice.infrastructure.persistence.entity.RecoveryCodeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface RecoveryCodeJpaRepository extends JpaRepository<RecoveryCodeEntity, UUID> {

    @Query("SELECT rc FROM RecoveryCodeEntity rc WHERE rc.user.id = :userId AND rc.usedAt IS NULL ORDER BY rc.createdAt ASC")
    List<RecoveryCodeEntity> findUnusedByUserId(@Param("userId") UUID userId);

    @Query("SELECT COUNT(rc) FROM RecoveryCodeEntity rc WHERE rc.user.id = :userId AND rc.usedAt IS NULL")
    long countUnusedByUserId(@Param("userId") UUID userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE RecoveryCodeEntity rc SET rc.usedAt = :usedAt WHERE rc.user.id = :userId AND rc.codeHash = :codeHash AND rc.usedAt IS NULL")
    int consume(@Param("userId") UUID userId, @Param("codeHash") String codeHash, @Param("usedAt") Instant usedAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM RecoveryCodeEntity rc WHERE rc.user.id = :userId")
    void deleteAllByUserId(@Param("userId") UUID userId);
}
