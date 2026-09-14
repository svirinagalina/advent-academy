package com.adventacademy.userservice.infrastructure.security;

import com.adventacademy.userservice.application.port.JwtProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class JjwtProvider implements JwtProvider {

    private static final String TOKEN_TYPE_CLAIM = "token_type";
    private static final String ACCESS_TOKEN = "access";
    private static final String REFRESH_TOKEN = "refresh";

    private final SecretKey signingKey;
    private final String issuer;
    private final String audience;
    private final Duration accessTokenLifetime;
    private final Duration refreshTokenLifetime;
    private final long clockSkewSeconds;
    private final Clock clock;
    /**
     * Active refresh tokens by JTI. Process-local until the durable refresh sessions of ADR 0003
     * land; user-wide revocation (password reset/change) is therefore per replica as well.
     */
    private final ConcurrentMap<String, ActiveRefreshToken> activeRefreshTokens = new ConcurrentHashMap<>();

    private record ActiveRefreshToken(UUID userId, Instant expiresAt) {}

    public JjwtProvider(
            @Value("${security.jwt.secret}") String secret,
            @Value("${security.jwt.issuer:advent-academy-user-service}") String issuer,
            @Value("${security.jwt.audience:advent-academy-services}") String audience,
            @Value("${security.jwt.access-token-expiration-minutes:15}") long accessTokenMinutes,
            @Value("${security.jwt.refresh-token-expiration-days:7}") long refreshTokenDays,
            @Value("${security.jwt.clock-skew-seconds:30}") long clockSkewSeconds,
            Clock clock) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("security.jwt.secret must contain at least 32 UTF-8 bytes");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = issuer;
        this.audience = audience;
        this.accessTokenLifetime = Duration.ofMinutes(accessTokenMinutes);
        this.refreshTokenLifetime = Duration.ofDays(refreshTokenDays);
        this.clockSkewSeconds = clockSkewSeconds;
        this.clock = clock;
    }

    @Override
    public String generateAccessToken(UUID userId, String email, Set<String> roles) {
        return generateToken(userId, ACCESS_TOKEN, accessTokenLifetime, email, roles);
    }

    @Override
    public String generateRefreshToken(UUID userId) {
        final String token = generateToken(userId, REFRESH_TOKEN, refreshTokenLifetime, null, Set.of());
        final Claims claims = parseClaims(token);
        activeRefreshTokens.put(claims.getId(), new ActiveRefreshToken(userId, claims.getExpiration().toInstant()));
        return token;
    }

    @Override
    public boolean validateAccessToken(String token) {
        return hasType(token, ACCESS_TOKEN);
    }

    @Override
    public boolean validateRefreshToken(String token) {
        try {
            final Claims claims = parseClaims(token);
            removeExpiredRefreshTokens();
            return REFRESH_TOKEN.equals(claims.get(TOKEN_TYPE_CLAIM, String.class))
                    && activeRefreshTokens.containsKey(claims.getId());
        } catch (JwtException | IllegalArgumentException exception) {
            return false;
        }
    }

    @Override
    public boolean consumeRefreshToken(String token) {
        try {
            final Claims claims = parseClaims(token);
            removeExpiredRefreshTokens();
            return REFRESH_TOKEN.equals(claims.get(TOKEN_TYPE_CLAIM, String.class))
                    && activeRefreshTokens.remove(claims.getId()) != null;
        } catch (JwtException | IllegalArgumentException exception) {
            return false;
        }
    }

    @Override
    public void revokeRefreshTokens(UUID userId) {
        activeRefreshTokens.values().removeIf(active -> active.userId().equals(userId));
    }

    @Override
    public UUID getUserIdFromToken(String token) {
        return UUID.fromString(parseClaims(token).getSubject());
    }

    @Override
    public Set<String> getRolesFromToken(String token) {
        final Object roles = parseClaims(token).get("roles");
        if (!(roles instanceof java.util.Collection<?> collection)) {
            return Set.of();
        }
        return collection.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private String generateToken(
            UUID userId,
            String tokenType,
            Duration lifetime,
            String email,
            Set<String> roles) {
        final Instant issuedAt = clock.instant();
        final var builder = Jwts.builder()
                .subject(userId.toString())
                .issuer(issuer)
                .audience().add(audience).and()
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(issuedAt.plus(lifetime)))
                .id(UUID.randomUUID().toString())
                .claim(TOKEN_TYPE_CLAIM, tokenType)
                .claim("roles", roles.stream().sorted().toList());
        if (email != null) {
            builder.claim("email", email);
        }
        return builder.signWith(signingKey).compact();
    }

    private boolean hasType(String token, String expectedType) {
        try {
            return expectedType.equals(parseClaims(token).get(TOKEN_TYPE_CLAIM, String.class));
        } catch (JwtException | IllegalArgumentException exception) {
            return false;
        }
    }

    private Claims parseClaims(String token) {
        final Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(issuer)
                .clockSkewSeconds(clockSkewSeconds)
                .clock(() -> Date.from(clock.instant()))
                .build()
                .parseSignedClaims(token)
                .getPayload();
        if (!claims.getAudience().contains(audience)) {
            throw new JwtException("Invalid token audience");
        }
        return claims;
    }

    private void removeExpiredRefreshTokens() {
        final Instant now = clock.instant();
        activeRefreshTokens.values().removeIf(active -> !active.expiresAt().isAfter(now));
    }
}
