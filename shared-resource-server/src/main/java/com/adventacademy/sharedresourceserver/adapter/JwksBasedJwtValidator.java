package com.adventacademy.sharedresourceserver.adapter;

import com.adventacademy.sharedresourceserver.adapter.exception.JwtValidationException;
import com.adventacademy.sharedresourceserver.port.JwtValidator;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.PublicKey;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Component
public class JwksBasedJwtValidator implements JwtValidator {

    private static final String TOKEN_TYPE_CLAIM = "token_type";
    private static final String ACCESS_TOKEN = "access";
    private static final String ALGORITHM = "RS256";

    private final JwksCacheProvider jwksCacheProvider;
    private final String expectedIssuer;
    private final String expectedAudience;
    private final long clockSkewSeconds;
    private final Clock clock;

    public JwksBasedJwtValidator(
            JwksCacheProvider jwksCacheProvider,
            @Value("${security.jwt.issuer:advent-academy-user-service}") String expectedIssuer,
            @Value("${security.jwt.audience:advent-academy-services}") String expectedAudience,
            @Value("${security.jwt.clock-skew-seconds:30}") long clockSkewSeconds,
            Clock clock) {
        this.jwksCacheProvider = jwksCacheProvider;
        this.expectedIssuer = expectedIssuer;
        this.expectedAudience = expectedAudience;
        this.clockSkewSeconds = clockSkewSeconds;
        this.clock = clock;
    }

    @Override
    public void validateToken(String token) {
        try {
            Claims claims = parseToken(token);

            // Validate token type
            String tokenType = claims.get(TOKEN_TYPE_CLAIM, String.class);
            if (!ACCESS_TOKEN.equals(tokenType)) {
                throw new JwtValidationException(
                        JwtValidationException.Reason.INVALID_TOKEN_TYPE,
                        "Expected " + ACCESS_TOKEN + " but got " + tokenType
                );
            }

            log.debug("Token validated successfully for user: {}", claims.getSubject());
        } catch (JwtValidationException e) {
            throw e;
        } catch (JwtException e) {
            log.warn("JWT validation failed", e);
            throw new JwtValidationException(JwtValidationException.Reason.INVALID_SIGNATURE, e);
        } catch (Exception e) {
            log.error("Unexpected error during token validation", e);
            throw new JwtValidationException(JwtValidationException.Reason.OTHER, e);
        }
    }

    @Override
    public UUID getUserIdFromToken(String token) {
        Claims claims = parseToken(token);
        return UUID.fromString(claims.getSubject());
    }

    @Override
    public Set<String> getRolesFromToken(String token) {
        Claims claims = parseToken(token);
        Object roles = claims.get("roles");
        if (!(roles instanceof java.util.Collection<?> collection)) {
            return Set.of();
        }
        return collection.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public String getTokenTypeFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get(TOKEN_TYPE_CLAIM, String.class);
    }

    @Override
    public Map<String, Object> getClaimsFromToken(String token) {
        Claims claims = parseToken(token);
        return new HashMap<>(claims);
    }

    private Claims parseToken(String token) {
        try {
            // Extract kid from header without verifying yet
            io.jsonwebtoken.Jws<Claims> jws = Jwts.parser()
                    .unsignedPayload()
                    .parseSignedClaims(token);

            String kid = (String) jws.getHeader().get("kid");
            if (kid == null) {
                throw new JwtValidationException(JwtValidationException.Reason.UNKNOWN_KID, "Missing 'kid' in JWT header");
            }

            // Get public key by kid
            PublicKey publicKey = jwksCacheProvider.getPublicKeyByKid(kid)
                    .orElseThrow(() -> new JwtValidationException(
                            JwtValidationException.Reason.UNKNOWN_KID,
                            "Unknown key ID: " + kid
                    ));

            // Verify signature and parse claims
            Claims claims = Jwts.parser()
                    .verifyWith(publicKey)
                    .requireIssuer(expectedIssuer)
                    .clockSkewSeconds(clockSkewSeconds)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            // Validate algorithm
            String algorithm = (String) jws.getHeader().get("alg");
            if (!ALGORITHM.equals(algorithm)) {
                throw new JwtValidationException(
                        JwtValidationException.Reason.INVALID_ALGORITHM,
                        "Expected " + ALGORITHM + " but got " + algorithm
                );
            }

            // Validate audience
            if (!claims.getAudience().contains(expectedAudience)) {
                throw new JwtValidationException(
                        JwtValidationException.Reason.INVALID_AUDIENCE,
                        "Invalid audience"
                );
            }

            // Check expiry (again, extra safety)
            if (claims.getExpiration().toInstant().isBefore(Instant.now())) {
                throw new JwtValidationException(
                        JwtValidationException.Reason.EXPIRED_TOKEN,
                        "Token has expired"
                );
            }

            return claims;
        } catch (JwtValidationException e) {
            throw e;
        } catch (JwtException e) {
            log.debug("JWT parsing failed", e);
            throw new JwtValidationException(JwtValidationException.Reason.MALFORMED_TOKEN, e);
        }
    }
}
