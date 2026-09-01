package com.adventacademy.userservice.infrastructure.security;

import com.adventacademy.userservice.application.port.JwtProvider;
import com.adventacademy.userservice.domain.repository.RsaKeyRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.StringReader;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Component
public class JjwtProvider implements JwtProvider {

    private static final String TOKEN_TYPE_CLAIM = "token_type";
    private static final String ACCESS_TOKEN = "access";
    private static final String REFRESH_TOKEN = "refresh";

    private final RsaKeyRepository rsaKeyRepository;
    private final String issuer;
    private final String audience;
    private final Duration accessTokenLifetime;
    private final Duration refreshTokenLifetime;
    private final long clockSkewSeconds;
    private final Clock clock;
    private final ConcurrentMap<String, Instant> activeRefreshTokens = new ConcurrentHashMap<>();

    public JjwtProvider(
            RsaKeyRepository rsaKeyRepository,
            @Value("${security.jwt.issuer:advent-academy-user-service}") String issuer,
            @Value("${security.jwt.audience:advent-academy-services}") String audience,
            @Value("${security.jwt.access-token-expiration-minutes:15}") long accessTokenMinutes,
            @Value("${security.jwt.refresh-token-expiration-days:7}") long refreshTokenDays,
            @Value("${security.jwt.clock-skew-seconds:30}") long clockSkewSeconds,
            Clock clock) {
        this.rsaKeyRepository = rsaKeyRepository;
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
        activeRefreshTokens.put(claims.getId(), claims.getExpiration().toInstant());
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
        var activeKey = rsaKeyRepository.findActive().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No active RSA key available"));

        PrivateKey privateKey = loadPrivateKeyFromPem(activeKey.getPrivateKeyPem());

        final Instant issuedAt = clock.instant();
        final var builder = Jwts.builder()
                .header()
                .add("kid", activeKey.getKid())
                .and()
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
        return builder.signWith(privateKey).compact();
    }

    private PrivateKey loadPrivateKeyFromPem(String pemKey) {
        try {
            String privateKeyPem = pemKey
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");

            byte[] decodedKey = Base64.getDecoder().decode(privateKeyPem);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decodedKey);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return keyFactory.generatePrivate(spec);
        } catch (Exception e) {
            log.error("Failed to load private key from PEM", e);
            throw new RuntimeException("Failed to load private key", e);
        }
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
        activeRefreshTokens.values().removeIf(expiry -> !expiry.isAfter(now));
    }
}
