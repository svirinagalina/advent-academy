package com.adventacademy.userservice.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Spring configuration for the {@link Clock} bean.
 * <p>
 * Exposes {@link Clock#systemDefaultZone()} so that services depending on
 * the current time can accept a {@code Clock} in their constructor and use
 * {@code Clock.fixed()} in unit tests for deterministic behaviour.
 * </p>
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
