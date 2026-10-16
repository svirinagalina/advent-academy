package com.adventacademy.userservice.infrastructure.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AuthRateLimitProperties.class)
public class AuthRateLimitConfiguration {

    /**
     * Registered explicitly (not as a {@code @Component}) so the URL pattern and the order are
     * visible here: only {@code /api/auth/*}, right after the request-id filter.
     */
    @Bean
    public FilterRegistrationBean<AuthRateLimitFilter> authRateLimitFilter(RateLimiter rateLimiter,
                                                                          AuthRateLimitProperties properties,
                                                                          ObjectMapper objectMapper,
                                                                          @org.springframework.beans.factory.annotation.Value("${security.bff-request-key:}") String bffKey) {
        var registration = new FilterRegistrationBean<>(new AuthRateLimitFilter(rateLimiter, properties, objectMapper, bffKey));
        registration.addUrlPatterns("/api/auth/*");
        registration.setOrder(2);
        registration.setName("authRateLimitFilter");
        return registration;
    }
}
