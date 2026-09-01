package com.adventacademy.contentservice.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.HandlerInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class SecurityHeadersConfiguration implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SecurityHeadersInterceptor());
    }

    @Slf4j
    public static class SecurityHeadersInterceptor implements HandlerInterceptor {

        @Override
        public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                   Object handler, Exception ex) {
            // Add X-Content-Type-Options: nosniff to all responses
            // This prevents MIME type sniffing attacks
            response.setHeader("X-Content-Type-Options", "nosniff");

            // Add Referrer-Policy for privacy
            response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");

            // Prevent browsers from running scripts that aren't properly declared
            response.setHeader("X-Frame-Options", "DENY");
        }
    }
}
