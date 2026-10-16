package com.adventacademy.userservice.infrastructure.ratelimit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;

/**
 * Throttles the public auth endpoints per client address and per account.
 * <p>
 * The client address is {@link HttpServletRequest#getRemoteAddr()} and nothing else: forwarded
 * headers are honoured only when the container is configured to trust the edge proxy
 * ({@code server.forward-headers-strategy=native} plus {@code server.tomcat.remoteip.*}), as
 * ADR 0005 requires. The account identity is the {@code email} field of the JSON body, which is
 * read once here and replayed to the controller.
 * <p>
 * Refusals are a bare {@code 429} with {@code Retry-After}; the body is the same for every scope,
 * so the limiter itself does not reveal whether an account exists.
 */
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AuthRateLimitFilter.class);
    /** Auth request bodies are a few hundred bytes; anything bigger is not a legitimate login. */
    static final int MAX_BODY_BYTES = 16 * 1024;
    private static final String RESPONSE_BODY = "{\"error\":\"Too many requests\",\"message\":\"Too many attempts. Please try again later.\"}";

    private String bffRequestKey;

    private final RateLimiter rateLimiter;
    private final AuthRateLimitProperties properties;
    private final ObjectMapper objectMapper;

    public AuthRateLimitFilter(RateLimiter rateLimiter, AuthRateLimitProperties properties, ObjectMapper objectMapper) {
        this(rateLimiter, properties, objectMapper, "");
    }

    public AuthRateLimitFilter(RateLimiter rateLimiter, AuthRateLimitProperties properties, ObjectMapper objectMapper, String bffRequestKey) {
        this.bffRequestKey = bffRequestKey.strip();
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !properties.enabled()
                || !HttpMethod.POST.matches(request.getMethod())
                || Scope.of(path(request)).isEmpty();
    }

    /** Path within the application, independent of how the container split servlet and path info. */
    private static String path(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        return context != null && !context.isEmpty() && uri.startsWith(context) ? uri.substring(context.length()) : uri;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Scope scope = Scope.of(path(request)).orElseThrow();

        byte[] body = readBody(request);
        if (body == null) {
            response.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
            return;
        }
        HttpServletRequest replayable = new CachedBodyHttpServletRequest(request, body);

        String clientIp = request.getRemoteAddr();
        String bucket = request.getHeader("X-Advent-Client");
        if (bucket != null) {
            if (!bucket.matches("[0-9a-f]{64}") || !com.adventacademy.security.SignedRequest.verify(
                    bffRequestKey, "client", path(request) + ":" + bucket, request.getHeader("X-Advent-Client-Proof"))) {
                response.setStatus(401);
                return;
            }
            clientIp = "bff:" + bucket;
        }
        if (!rateLimiter.tryAcquire(scope.name() + ":ip", clientIp, scope.ipLimit(properties), properties.window())) {
            reject(response, scope, "ip");
            return;
        }
        if (scope.accountLimit(properties) > 0) {
            Optional<String> account = extractEmail(body);
            if (account.isPresent()
                    && !rateLimiter.tryAcquire(scope.name() + ":account", account.get(), scope.accountLimit(properties), properties.window())) {
                reject(response, scope, "account");
                return;
            }
        }

        chain.doFilter(replayable, response);
    }

    /** @return the body, or {@code null} when it exceeds {@link #MAX_BODY_BYTES} */
    private static byte[] readBody(HttpServletRequest request) throws IOException {
        try (InputStream in = request.getInputStream()) {
            byte[] body = in.readNBytes(MAX_BODY_BYTES + 1);
            return body.length > MAX_BODY_BYTES ? null : body;
        }
    }

    private Optional<String> extractEmail(byte[] body) {
        if (body.length == 0) {
            return Optional.empty();
        }
        try {
            JsonNode email = objectMapper.readTree(body).path("email");
            return email.isTextual() && !email.asText().isBlank()
                    ? Optional.of(email.asText().trim().toLowerCase(Locale.ROOT))
                    : Optional.empty();
        } catch (IOException malformed) {
            return Optional.empty(); // the controller will answer 400; the IP limit already counted it
        }
    }

    private void reject(HttpServletResponse response, Scope scope, String by) throws IOException {
        // Deliberately no identity in the log line: neither the address nor the account.
        log.warn("Rate limit exceeded: scope={} by={}", scope.name(), by);
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(properties.window().toSeconds()));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(RESPONSE_BODY);
    }

    /** The endpoints under quota and where their limits come from. */
    enum Scope {
        LOGIN("/api/auth/login") {
            int ipLimit(AuthRateLimitProperties p) { return p.loginPerIp(); }
            int accountLimit(AuthRateLimitProperties p) { return p.loginPerAccount(); }
        },
        REGISTER("/api/auth/register") {
            int ipLimit(AuthRateLimitProperties p) { return p.registerPerIp(); }
            int accountLimit(AuthRateLimitProperties p) { return 0; }
        },
        PASSWORD_RESET("/api/auth/password-reset") {
            int ipLimit(AuthRateLimitProperties p) { return p.passwordResetPerIp(); }
            int accountLimit(AuthRateLimitProperties p) { return p.passwordResetPerAccount(); }
        },
        PASSWORD_RESET_CONFIRM("/api/auth/password-reset/confirm") {
            int ipLimit(AuthRateLimitProperties p) { return p.passwordResetPerIp(); }
            int accountLimit(AuthRateLimitProperties p) { return 0; }
        },
        INVITATION_ACCEPT("/api/auth/invitations/accept") {
            int ipLimit(AuthRateLimitProperties p) { return p.passwordResetPerIp(); }
            int accountLimit(AuthRateLimitProperties p) { return 0; }
        },
        RESEND_VERIFICATION("/api/auth/resend-verification") {
            int ipLimit(AuthRateLimitProperties p) { return p.resendVerificationPerIp(); }
            int accountLimit(AuthRateLimitProperties p) { return p.resendVerificationPerAccount(); }
        };

        private final String path;

        Scope(String path) {
            this.path = path;
        }

        abstract int ipLimit(AuthRateLimitProperties properties);

        /** @return 0 when the scope has no per-account quota */
        abstract int accountLimit(AuthRateLimitProperties properties);

        static Optional<Scope> of(String path) {
            if (path == null) {
                return Optional.empty();
            }
            String normalized = path.endsWith("/") && path.length() > 1
                    ? path.substring(0, path.length() - 1) : path;
            for (Scope scope : values()) {
                if (scope.path.equals(normalized)) {
                    return Optional.of(scope);
                }
            }
            return Optional.empty();
        }
    }
}
