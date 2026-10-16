package com.adventacademy.meetingservice.infrastructure.config;

import java.sql.Connection;
import javax.sql.DataSource;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Current topology has one transport node; reject accidental replicas instead of splitting a room. */
@Component
@Profile("production")
public class MeetingPlacementLease implements org.springframework.beans.factory.InitializingBean, org.springframework.beans.factory.DisposableBean {
    private final DataSource source;
    private final ConfigurableApplicationContext context;
    private Connection lease;
    public MeetingPlacementLease(DataSource source, ConfigurableApplicationContext context) { this.source = source; this.context = context; }
    @Override
    public void afterPropertiesSet() throws Exception {
        lease = source.getConnection();
        try (var statement = lease.createStatement(); var rows = statement.executeQuery("SELECT pg_try_advisory_lock(20465402)")) {
            if (!rows.next() || !rows.getBoolean(1)) {
                lease.close();
                throw new IllegalStateException("Another meeting transport node owns this database; explicit room sharding is required for replicas");
            }
        }
    }
    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 2000)
    public void check() throws Exception {
        if (lease == null || !lease.isValid(2)) { context.close(); }
    }
    @Override
    public void destroy() throws Exception {
        if (lease != null && !lease.isClosed()) {
            try (var statement = lease.createStatement()) { statement.execute("SELECT pg_advisory_unlock(20465402)"); }
            finally { lease.close(); }
        }
    }
}
