package com.adventacademy.userservice.adapter.rest.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Emits one access-log line per HTTP request with method, path, status and
 * latency. The structured (logstash) encoder picks up the MDC entries so the
 * resulting JSON line carries {@code requestId}, {@code method}, {@code path},
 * {@code status} and {@code durationMs} alongside any SQL or error logs that
 * fired during the same request — making the three log streams joinable by
 * {@code X-Request-Id}.
 * <p>
 * Ordered immediately after {@link RequestIdFilter} so the requestId is
 * already in MDC when this filter runs.
 * </p>
 */
@Component
@Order(2)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger("http.access");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        long startNanos = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
            MDC.put("method", request.getMethod());
            MDC.put("path", request.getRequestURI());
            MDC.put("status", String.valueOf(response.getStatus()));
            MDC.put("durationMs", String.valueOf(durationMs));
            try {
                log.info("{} {} -> {} ({}ms)",
                        request.getMethod(),
                        request.getRequestURI(),
                        response.getStatus(),
                        durationMs);
            } finally {
                MDC.remove("method");
                MDC.remove("path");
                MDC.remove("status");
                MDC.remove("durationMs");
            }
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs");
    }
}
