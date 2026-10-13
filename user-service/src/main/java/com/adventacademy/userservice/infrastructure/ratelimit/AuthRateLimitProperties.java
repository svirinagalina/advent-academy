package com.adventacademy.userservice.infrastructure.ratelimit;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Limits for the public auth endpoints, per fixed window. "Account" limits key on the e-mail in the
 * request body, so a distributed guess at one account is throttled no matter how many addresses
 * it comes from; "IP" limits key on the resolved client address (see ADR 0005).
 */
@Validated
@ConfigurationProperties(prefix = "security.rate-limit")
public record AuthRateLimitProperties(
        @DefaultValue("true") boolean enabled,
        @NotNull @DefaultValue("1h") Duration window,
        @Min(1) @DefaultValue("20") int loginPerIp,
        @Min(1) @DefaultValue("10") int loginPerAccount,
        @Min(1) @DefaultValue("10") int registerPerIp,
        @Min(1) @DefaultValue("5") int passwordResetPerIp,
        @Min(1) @DefaultValue("3") int passwordResetPerAccount,
        @Min(1) @DefaultValue("5") int resendVerificationPerIp,
        @Min(1) @DefaultValue("3") int resendVerificationPerAccount
) {}
