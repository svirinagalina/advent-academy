package com.adventacademy.userservice.infrastructure.ratelimit;

import java.time.Duration;

/** Fixed-window request counter shared by every replica of the service. */
public interface RateLimiter {

    /**
     * Counts one request for {@code identity} within {@code scope}.
     *
     * @return {@code true} when the request is within {@code limit} for the current window
     */
    boolean tryAcquire(String scope, String identity, int limit, Duration window);
}
