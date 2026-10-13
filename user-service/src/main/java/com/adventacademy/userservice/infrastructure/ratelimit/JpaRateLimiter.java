package com.adventacademy.userservice.infrastructure.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Fixed-window counters in {@code auth_rate_limit_buckets}, so all replicas share one view of how
 * often an address or account knocked on the auth endpoints (issue #45, "distributed rate limiting").
 * <p>
 * One request is one conditional {@code UPDATE ... WHERE request_count < limit}; a missing bucket
 * is inserted with ON CONFLICT DO NOTHING before the conditional update. Each call runs in its own short
 * transaction ({@code REQUIRES_NEW}) so the count survives even when the request itself fails.
 * Identities are stored as a digest: the table must not become a list of e-mail addresses.
 * Stale windows are reaped opportunistically.
 */
@Component
public class JpaRateLimiter implements RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(JpaRateLimiter.class);
    private static final Duration CLEANUP_INTERVAL = Duration.ofMinutes(5);

    private final RateLimitBucketJpaRepository buckets;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private final AtomicLong lastCleanupEpochSecond = new AtomicLong();

    public JpaRateLimiter(RateLimitBucketJpaRepository buckets, PlatformTransactionManager transactionManager, Clock clock) {
        this.buckets = buckets;
        this.transaction = new TransactionTemplate(transactionManager);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
    }

    @Override
    public boolean tryAcquire(String scope, String identity, int limit, Duration window) {
        Instant now = clock.instant();
        long windowSeconds = window.toSeconds();
        Instant windowStart = Instant.ofEpochSecond(Math.floorDiv(now.getEpochSecond(), windowSeconds) * windowSeconds);
        String key = scope + ":" + digest(identity) + ":" + windowStart.getEpochSecond();

        try {
            Boolean allowed = transaction.execute(status -> acquire(key, windowStart, limit));
            reapStaleWindows(now, window);
            return Boolean.TRUE.equals(allowed);
        } catch (DataAccessException e) {
            // Fail closed: if the counter store is unreachable the public auth endpoints wait.
            log.error("Rate limiter unavailable ({}); refusing request for scope {}", e.getClass().getSimpleName(), scope);
            return false;
        }
    }

    private boolean acquire(String key, Instant windowStart, int limit) {
        buckets.insertIfMissing(key, windowStart);
        return buckets.incrementIfBelow(key, limit) == 1;
    }

    private void reapStaleWindows(Instant now, Duration window) {
        long last = lastCleanupEpochSecond.get();
        if (now.getEpochSecond() - last < CLEANUP_INTERVAL.toSeconds()
                || !lastCleanupEpochSecond.compareAndSet(last, now.getEpochSecond())) {
            return;
        }
        try {
            transaction.executeWithoutResult(status -> buckets.deleteWindowsBefore(now.minus(window.multipliedBy(2))));
        } catch (DataAccessException e) {
            log.warn("Rate limit bucket cleanup skipped: {}", e.getClass().getSimpleName());
        }
    }

    private static String digest(String identity) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(identity.getBytes(StandardCharsets.UTF_8)), 0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
