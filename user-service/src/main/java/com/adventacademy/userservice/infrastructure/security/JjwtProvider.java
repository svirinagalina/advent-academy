package com.adventacademy.userservice.infrastructure.security;

import com.adventacademy.userservice.application.port.JwtProvider;
import com.adventacademy.userservice.application.port.RefreshTokenStore;
import com.adventacademy.userservice.application.service.auth.OpaqueTokens;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import java.util.Set;

@Component
public class JjwtProvider implements JwtProvider {

    private static final String TOKEN_TYPE_CLAIM = "token_type";
    private static final String ACCESS_TOKEN = "access";
    private static final String REFRESH_TOKEN = "refresh";

    private com.adventacademy.security.JwtKeys keys;
    private final String issuer;
    private final String audience;
    private final Duration accessTokenLifetime;
    private final Duration refreshTokenLifetime;
    private final long clockSkewSeconds;
    private final Clock clock;
    private final RefreshTokenStore refreshTokens;

    public JjwtProvider(
            @Value("#{environment.getProperty('security.jwt.secret','').isEmpty() ? 'construction-only-placeholder-key-32-bytes' : environment.getProperty('security.jwt.secret')}") String secret,
            @Value("${security.jwt.issuer:advent-academy-user-service}") String issuer,
            @Value("${security.jwt.audience:advent-academy-services}") String audience,
            @Value("${security.jwt.access-token-expiration-minutes:15}") long accessTokenMinutes,
            @Value("${security.jwt.refresh-token-expiration-days:7}") long refreshTokenDays,
            @Value("${security.jwt.clock-skew-seconds:30}") long clockSkewSeconds,
            Clock clock,
            RefreshTokenStore refreshTokens) {
        this.keys = new com.adventacademy.security.JwtKeys(secret, "", "", "", "", "");
        this.issuer = issuer;
        this.audience = audience;
        this.accessTokenLifetime = Duration.ofMinutes(accessTokenMinutes);
        this.refreshTokenLifetime = Duration.ofDays(refreshTokenDays);
        this.clockSkewSeconds = clockSkewSeconds;
        this.clock = clock;
        this.refreshTokens = refreshTokens;
    }

    @org.springframework.beans.factory.annotation.Autowired
    public void configureKeys(
            @Value("${security.jwt.secret:}") String secret,
            @Value("${security.jwt.private-key-file:}") String privateFile,
            @Value("${security.jwt.public-key-file:}") String publicFile,
            @Value("${security.jwt.key-id:}") String kid,
            @Value("${security.jwt.previous-public-key-file:}") String previousFile,
            @Value("${security.jwt.previous-key-id:}") String previousKid,
            org.springframework.core.env.Environment environment) {
        keys = new com.adventacademy.security.JwtKeys(secret, privateFile, publicFile, kid, previousFile, previousKid);
        if (keys.asymmetric() && !keys.canSign()) { throw new IllegalStateException("Identity service requires its private signing key"); }
        if (environment.acceptsProfiles(org.springframework.core.env.Profiles.of("production")) && !keys.asymmetric()) {
            throw new IllegalStateException("Production requires asymmetric JWT keys");
        }
    }

    public java.util.Map<String, Object> jwks() { return keys.jwks(); }

    @Override
    public String generateAccessToken(UUID userId, String email, Set<String> roles) {
        return generateToken(userId, ACCESS_TOKEN, accessTokenLifetime, email, roles);
    }

    @Override
    public String generateRefreshToken(UUID userId) {
        final String token = generateToken(userId, REFRESH_TOKEN, refreshTokenLifetime, null, Set.of());
        final Claims claims = parseClaims(token);
        refreshTokens.save(OpaqueTokens.digest(claims.getId()), userId, claims.getExpiration().toInstant());
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
            return REFRESH_TOKEN.equals(claims.get(TOKEN_TYPE_CLAIM, String.class))
                    && refreshTokens.isActive(OpaqueTokens.digest(claims.getId()), clock.instant());
        } catch (JwtException | IllegalArgumentException exception) {
            return false;
        }
    }

    @Override
    public boolean consumeRefreshToken(String token) {
        try {
            final Claims claims = parseClaims(token);
            return REFRESH_TOKEN.equals(claims.get(TOKEN_TYPE_CLAIM, String.class))
                    && refreshTokens.consume(OpaqueTokens.digest(claims.getId()), clock.instant());
        } catch (JwtException | IllegalArgumentException exception) {
            return false;
        }
    }

    @Override
    public void revokeRefreshTokens(UUID userId) {
        refreshTokens.revokeByUserId(userId);
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
        return keys.sign(builder);
    }

    private boolean hasType(String token, String expectedType) {
        try {
            return expectedType.equals(parseClaims(token).get(TOKEN_TYPE_CLAIM, String.class));
        } catch (JwtException | IllegalArgumentException exception) {
            return false;
        }
    }

    private Claims parseClaims(String token) {
        final Claims claims = keys.parse(token, issuer, audience, clockSkewSeconds, clock);
        return claims;
    }

}
