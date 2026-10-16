package com.adventacademy.userservice.application.port;

import java.util.UUID;
import java.util.Set;

public interface JwtProvider {
    String generateAccessToken(UUID userId, String email, Set<String> roles);
    String generateRefreshToken(UUID userId);
    String rotateRefreshToken(String previousToken);
    void revokeReplayedRefreshToken(String token);
    boolean validateAccessToken(String token);
    boolean validateRefreshToken(String token);
    boolean consumeRefreshToken(String token);
    UUID getUserIdFromToken(String token);
    Set<String> getRolesFromToken(String token);

    /** Invalidates every refresh token issued to the user, e.g. after a password reset. */
    void revokeRefreshTokens(UUID userId);
}
