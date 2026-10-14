package com.adventacademy.meetingservice.infrastructure.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Date;
import java.util.UUID;

@Component
public class AccessTokenVerifier {

    private final SecretKey signingKey;
    private final String issuer;
    private final String audience;
    private final long clockSkewSeconds;
    private final Clock clock;

    public AccessTokenVerifier(
            @Value("${security.jwt.secret}") String secret,
            @Value("${security.jwt.issuer:advent-academy-user-service}") String issuer,
            @Value("${security.jwt.audience:advent-academy-services}") String audience,
            @Value("${security.jwt.clock-skew-seconds:30}") long clockSkewSeconds,
            Clock clock) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("security.jwt.secret must contain at least 32 UTF-8 bytes");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = issuer;
        this.audience = audience;
        this.clockSkewSeconds = clockSkewSeconds;
        this.clock = clock;
    }

    public record Identity(UUID userId, java.util.List<String> roles) { }

    public UUID verify(String token) {
        return verifyIdentity(token).userId();
    }

    public Identity verifyIdentity(String token) {
        final Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(issuer)
                .clockSkewSeconds(clockSkewSeconds)
                .clock(() -> Date.from(clock.instant()))
                .build()
                .parseSignedClaims(token)
                .getPayload();
        if (!claims.getAudience().contains(audience)
                || !"access".equals(claims.get("token_type", String.class))) {
            throw new JwtException("Invalid access token scope");
        }
        final java.util.List<String> roles = new java.util.ArrayList<>();
        final Object rawRoles = claims.get("roles");
        if (rawRoles instanceof java.util.Collection<?> values) {
            for (Object value : values) {
                if (!(value instanceof String role) || !role.matches("[A-Z_]{1,64}")) {
                    throw new JwtException("Invalid role claim");
                }
                roles.add(role);
            }
        } else if (rawRoles != null) {
            throw new JwtException("Invalid role claim");
        }
        return new Identity(UUID.fromString(claims.getSubject()), java.util.List.copyOf(roles));
    }
}
