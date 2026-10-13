package com.adventacademy.userservice.infrastructure.persistence.jpa;

import com.adventacademy.userservice.infrastructure.persistence.entity.RefreshTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface RefreshTokenJpaRepository extends JpaRepository<RefreshTokenEntity, String> {

    @Query("select count(t) > 0 from RefreshTokenEntity t where t.jtiDigest = :digest and t.expiresAt > :now")
    boolean isActive(@Param("digest") String digest, @Param("now") Instant now);

    @Modifying(flushAutomatically = true)
    @Query("delete from RefreshTokenEntity t where t.jtiDigest = :digest and t.expiresAt > :now")
    int consume(@Param("digest") String digest, @Param("now") Instant now);

    @Modifying(flushAutomatically = true)
    @Query("delete from RefreshTokenEntity t where t.userId = :userId")
    int revokeByUserId(@Param("userId") UUID userId);

    @Modifying(flushAutomatically = true)
    @Query("delete from RefreshTokenEntity t where t.userId = :userId and t.expiresAt <= :now")
    int deleteExpiredByUserId(@Param("userId") UUID userId, @Param("now") Instant now);
}
