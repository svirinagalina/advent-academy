package com.adventacademy.meetingservice.infrastructure.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Date;
import java.util.UUID;

@Component
public class AccessTokenVerifier {

    private com.adventacademy.security.JwtKeys keys;
    private final String issuer;
    private final String audience;
    private final long clockSkewSeconds;
    private final Clock clock;

    public AccessTokenVerifier(
            @Value("#{environment.getProperty('security.jwt.secret','').isEmpty() "
                    + "? 'construction-only-placeholder-key-32-bytes' : environment.getProperty('security.jwt.secret')}") String secret,
            @Value("${security.jwt.issuer:advent-academy-user-service}") String issuer,
            @Value("${security.jwt.audience:advent-academy-services}") String audience,
            @Value("${security.jwt.clock-skew-seconds:30}") long clockSkewSeconds,
            Clock clock) {
        this.keys = new com.adventacademy.security.JwtKeys(secret, "", "", "", "", "");
        this.issuer = issuer;
        this.audience = audience;
        this.clockSkewSeconds = clockSkewSeconds;
        this.clock = clock;
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

    public record Identity(UUID userId, java.util.List<String> roles) { }

    public UUID verify(String token) {
        return verifyIdentity(token).userId();
    }

    public Identity verifyIdentity(String token) {
        final Claims claims = keys.parse(token, issuer, audience, clockSkewSeconds, clock);
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
