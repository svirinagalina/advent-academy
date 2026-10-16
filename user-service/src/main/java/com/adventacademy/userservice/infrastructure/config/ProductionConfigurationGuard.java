package com.adventacademy.userservice.infrastructure.config;

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

        require("security.bff-request-key", 32);
        require("enrollment.validation-key", 32);
        if (!environment.getProperty("spring.mail.enabled", Boolean.class, false)) {
            throw new IllegalStateException("Production requires real SMTP delivery");
        }
        if (!environment.getRequiredProperty("app.baseurl").startsWith("https://")) {
            throw new IllegalStateException("Production links require HTTPS");
        }

    }
    private void require(String key, int min) {
        if (environment.getProperty(key, "").length() < min) { throw new IllegalStateException("Missing production setting: " + key); }
    }
}
