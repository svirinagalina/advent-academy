package com.adventacademy.userservice.infrastructure.ratelimit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

interface RateLimitBucketJpaRepository extends JpaRepository<RateLimitBucketEntity, String> {

    @Modifying(flushAutomatically = true)
    @Query(value = "INSERT INTO auth_rate_limit_buckets (bucket_key, window_start, request_count) "
            + "VALUES (:key, :windowStart, 0) ON CONFLICT (bucket_key) DO NOTHING", nativeQuery = true)
    int insertIfMissing(@Param("key") String key, @Param("windowStart") Instant windowStart);

    /** @return 1 when the bucket existed and had room, 0 when it is missing or exhausted */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE RateLimitBucketEntity b SET b.requestCount = b.requestCount + 1 WHERE b.bucketKey = :key AND b.requestCount < :limit")
    int incrementIfBelow(@Param("key") String key, @Param("limit") int limit);

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM RateLimitBucketEntity b WHERE b.windowStart < :before")
    int deleteWindowsBefore(@Param("before") Instant before);
}
