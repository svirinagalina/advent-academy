package com.adventacademy.userservice.infrastructure.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Simple in-memory rate limiting for auth endpoints.
 * Production: use Redis for distributed rate limiting.
 *
 * Rate limits:
 * - /register: 10 per hour per IP
 * - /login: 10 per hour per email
 * - /password-reset: 5 per hour per IP
 */
@Slf4j
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final int registerPerHour;
    private final int loginPerHour;
    private final int resetPerHour;
    private final ConcurrentHashMap<String, ConcurrentLinkedQueue<Long>> requestTimestamps = new ConcurrentHashMap<>();

    public RateLimitInterceptor(
            @Value("${security.rate-limit.register-per-ip-per-hour:10}") int registerPerHour,
            @Value("${security.rate-limit.login-per-email-per-hour:10}") int loginPerHour,
            @Value("${security.rate-limit.password-reset-per-ip-per-hour:5}") int resetPerHour
    ) {
        this.registerPerHour = registerPerHour;
        this.loginPerHour = loginPerHour;
        this.resetPerHour = resetPerHour;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = request.getRequestURI();
        String clientIp = getClientIp(request);

        if (path.contains("/register")) {
            if (!isAllowed(clientIp, registerPerHour)) {
                response.setStatus(429); // Too Many Requests
                response.getWriter().write("{\"error\": \"Too many registration attempts. Try again later.\"}");
                return false;
            }
        } else if (path.contains("/login")) {
            String email = request.getParameter("email");
            if (!isAllowed(email != null ? email : clientIp, loginPerHour)) {
                response.setStatus(429);
                response.getWriter().write("{\"error\": \"Too many login attempts. Try again later.\"}");
                return false;
            }
        } else if (path.contains("/password-reset")) {
            if (!isAllowed(clientIp, resetPerHour)) {
                response.setStatus(429);
                response.getWriter().write("{\"error\": \"Too many password reset attempts. Try again later.\"}");
                return false;
            }
        }

        return true;
    }

    private boolean isAllowed(String key, int limit) {
        long now = System.currentTimeMillis();
        long oneHourAgo = now - 3600000;

        ConcurrentLinkedQueue<Long> timestamps = requestTimestamps.computeIfAbsent(key, k -> new ConcurrentLinkedQueue<>());

        // Remove old requests
        timestamps.removeIf(t -> t < oneHourAgo);

        if (timestamps.size() < limit) {
            timestamps.add(now);
            return true;
        }

        log.warn("Rate limit exceeded for key: {}", key);
        return false;
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty()) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty()) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
}
