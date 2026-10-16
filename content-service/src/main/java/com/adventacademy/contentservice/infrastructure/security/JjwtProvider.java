package com.adventacademy.contentservice.infrastructure.security;

import com.adventacademy.contentservice.application.port.JwtProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
public class JjwtProvider implements JwtProvider {
    private com.adventacademy.security.JwtKeys keys;
    private final String issuer;
    private final String audience;
    private final int clockSkewSeconds;

    public JjwtProvider(
            @Value("#{environment.getProperty('security.jwt.secret','').isEmpty() ? 'construction-only-placeholder-key-32-bytes' : environment.getProperty('security.jwt.secret')}") String secret,
            @Value("${security.jwt.issuer}") String issuer,
            @Value("${security.jwt.audience}") String audience,
            @Value("${security.jwt.clock-skew-seconds}") int clockSkewSeconds
    ) {
        this.keys = new com.adventacademy.security.JwtKeys(secret, "", "", "", "", "");
        this.issuer = issuer;
        this.audience = audience;
        this.clockSkewSeconds = clockSkewSeconds;
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
        keys = new com.adventacademy.security.JwtKeys(secret, "", publicFile, kid, previousFile, previousKid);
        if (environment.acceptsProfiles(org.springframework.core.env.Profiles.of("production")) && !keys.asymmetric()) {
            throw new IllegalStateException("Production requires asymmetric JWT keys");
        }
    }

    @Override
    public ValidatedToken validate(String token) throws JwtException {
        try {
            Claims claims = keys.parse(token, issuer, audience, clockSkewSeconds, java.time.Clock.systemUTC());

            validateClaims(claims);

            UUID userId = parseUserId(claims);
            Set<String> roles = extractRoles(claims);

            return new ValidatedToken(userId, roles);
        } catch (JwtException e) {
            log.debug("JWT validation failed: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.debug("Token validation error: {}", e.getMessage());
            throw new JwtException("Invalid subject");
        }
    }

    private UUID parseUserId(Claims claims) throws JwtException {
        String subject = claims.getSubject();
        if (subject == null) {
            throw new JwtException("Invalid subject");
        }
        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException e) {
            throw new JwtException("Invalid subject");
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
