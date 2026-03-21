package com.adventacademy.userservice.infrastructure.security;

import com.adventacademy.userservice.application.port.JwtProvider;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class JwtProviderStub implements JwtProvider {

    // TODO: Implement JWT generation using io.jsonwebtoken.Jwts
    // Example implementation:
    // - Use Jwts.builder()
    // - Set subject (userId), issuer, issuedAt, expiration
    // - Add custom claims (email, roles, etc.)
    // - Sign with secret from application.yml (security.jwt.secret)
    // - Use HS256 algorithm
    //
    // Dependencies needed:
    // - io.jsonwebtoken:jjwt-api
    // - io.jsonwebtoken:jjwt-impl
    // - io.jsonwebtoken:jjwt-jackson

    @Override
    public String generateAccessToken(UUID userId, String email) {
        // TODO: Use secret from @Value("${security.jwt.secret}")
        // TODO: Set expiration from @Value("${security.jwt.access-token-expiration-minutes}")
        // TODO: Build JWT with claims: sub=userId, email=email
        throw new UnsupportedOperationException("JWT generation not implemented yet. Please implement using jjwt library.");
    }

    @Override
    public String generateRefreshToken(UUID userId) {
        // TODO: Similar to access token but with longer expiration
        // TODO: Use expiration from @Value("${security.jwt.refresh-token-expiration-days}")
        throw new UnsupportedOperationException("JWT refresh token generation not implemented yet");
    }

    @Override
    public boolean validateToken(String token) {
        // TODO: Parse token, validate signature and expiration
        // TODO: Handle ExpiredJwtException, MalformedJwtException, etc.
        throw new UnsupportedOperationException("JWT validation not implemented yet");
    }

    @Override
    public UUID getUserIdFromToken(String token) {
        // TODO: Parse token, extract subject (userId)
        // TODO: Convert string to UUID
        throw new UnsupportedOperationException("JWT parsing not implemented yet");
    }
}
