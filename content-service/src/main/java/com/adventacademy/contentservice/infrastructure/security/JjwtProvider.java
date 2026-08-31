package com.adventacademy.contentservice.infrastructure.security;

import com.adventacademy.contentservice.application.port.JwtProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
public class JjwtProvider implements JwtProvider {
    private final SecretKey secretKey;
    private final String issuer;
    private final String audience;
    private final int clockSkewSeconds;

    public JjwtProvider(
            @Value("${security.jwt.secret}") String secret,
            @Value("${security.jwt.issuer}") String issuer,
            @Value("${security.jwt.audience}") String audience,
            @Value("${security.jwt.clock-skew-seconds}") int clockSkewSeconds
    ) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = issuer;
        this.audience = audience;
        this.clockSkewSeconds = clockSkewSeconds;
    }

    @Override
    public ValidatedToken validate(String token) throws JwtException {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .clockSkewSeconds(clockSkewSeconds)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            validateClaims(claims);

            UUID userId = UUID.fromString(claims.getSubject());
            Set<String> roles = extractRoles(claims);

            return new ValidatedToken(userId, roles);
        } catch (JwtException e) {
            log.debug("JWT validation failed: {}", e.getMessage());
            throw e;
        }
    }

    private void validateClaims(Claims claims) throws JwtException {
        String tokenType = claims.get("token_type", String.class);
        if (!"access".equals(tokenType)) {
            throw new JwtException("Invalid token_type: expected 'access'");
        }

        if (!issuer.equals(claims.getIssuer())) {
            throw new JwtException("Invalid issuer");
        }

        Set<String> tokenAudience = claims.getAudience();
        if (tokenAudience == null || !tokenAudience.contains(audience)) {
            throw new JwtException("Invalid audience");
        }
    }

    @SuppressWarnings("unchecked")
    private Set<String> extractRoles(Claims claims) {
        List<String> rolesList = (List<String>) claims.get("roles", List.class);
        if (rolesList == null) {
            return new HashSet<>();
        }
        return new HashSet<>(rolesList);
    }
}
