package com.adventacademy.userservice.application.port;

import java.util.UUID;
import java.util.Set;

public interface JwtProvider {
    String generateAccessToken(UUID userId, String email, Set<String> roles);
    String generateRefreshToken(UUID userId);
    boolean validateAccessToken(String token);
    boolean validateRefreshToken(String token);
    boolean consumeRefreshToken(String token);
    UUID getUserIdFromToken(String token);
    Set<String> getRolesFromToken(String token);
}
