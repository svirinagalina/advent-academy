package com.adventacademy.userservice.infrastructure.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Independent cleanup never holds user/advisory locks, avoiding a cross-user lock-order cycle. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "security.refresh-receipts.cleanup-enabled", havingValue = "true")
public class RotationReceiptCleanup {
    private final JdbcTemplate jdbc;
    public RotationReceiptCleanup(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Scheduled(fixedDelay = 60000, initialDelay = 60000)
    public void removeExpired() {
        jdbc.update("DELETE FROM refresh_rotation_receipts WHERE token_digest IN "
                + "(SELECT token_digest FROM refresh_rotation_receipts WHERE expires_at<=CURRENT_TIMESTAMP "
                + "ORDER BY expires_at LIMIT 1000 FOR UPDATE SKIP LOCKED)");
    }
}
