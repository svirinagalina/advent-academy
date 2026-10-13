package com.adventacademy.userservice.infrastructure.ratelimit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "auth_rate_limit_buckets")
public class RateLimitBucketEntity {

    @Id
    @Column(name = "bucket_key", length = 255, nullable = false)
    private String bucketKey;

    @Column(name = "window_start", nullable = false)
    private Instant windowStart;

    @Column(name = "request_count", nullable = false)
    private int requestCount;

    protected RateLimitBucketEntity() {
    }

    RateLimitBucketEntity(String bucketKey, Instant windowStart, int requestCount) {
        this.bucketKey = bucketKey;
        this.windowStart = windowStart;
        this.requestCount = requestCount;
    }

    public String getBucketKey() {
        return bucketKey;
    }

    public Instant getWindowStart() {
        return windowStart;
    }

    public int getRequestCount() {
        return requestCount;
    }
}
