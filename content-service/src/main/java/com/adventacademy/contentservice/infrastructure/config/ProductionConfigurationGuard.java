package com.adventacademy.contentservice.infrastructure.config;

import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@Profile("production")
public class ProductionConfigurationGuard implements org.springframework.beans.factory.InitializingBean {
    private final Environment environment;
    public ProductionConfigurationGuard(Environment environment) { this.environment = environment; }
    @Override
    public void afterPropertiesSet() {
        require("security.jwt.public-key-file", 1);
        if (!environment.getProperty("security.jwt.secret", "").isBlank()) {
            throw new IllegalStateException("Production does not distribute HMAC signing secrets");
        }
        String runtime = environment.getRequiredProperty("spring.datasource.username");
        String migration = environment.getRequiredProperty("spring.flyway.user");
        if (runtime.equals(migration)) { throw new IllegalStateException("Separate migration and runtime identities are required"); }

        if (!environment.getProperty("security.jwt.private-key-file", "").isBlank()) {
            throw new IllegalStateException("Resource services must not receive a JWT private key");
        }
        require("enrollment.validation-key", 32);

    }
    private void require(String key, int min) {
        if (environment.getProperty(key, "").length() < min) { throw new IllegalStateException("Missing production setting: " + key); }
    }
}
