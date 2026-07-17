package com.adventacademy.contentservice;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for integration tests running against a real PostgreSQL
 * started in Docker by Testcontainers. Flyway migrations from
 * src/main/resources/db/migration are applied on startup, so tests verify
 * the real schema including DEFERRABLE constraints and foreign keys
 * (H2 used in the "test" profile cannot do that).
 * <p>
 * The container is started once per JVM (singleton pattern) and shared
 * by all subclasses; Testcontainers removes it automatically afterwards.
 */
@SpringBootTest
public abstract class AbstractPostgresIT {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }
}
