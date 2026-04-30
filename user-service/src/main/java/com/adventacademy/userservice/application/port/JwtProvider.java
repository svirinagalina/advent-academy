package com.adventacademy.userservice.application.port;

import java.util.UUID;

public interface JwtProvider {
    String generateAccessToken(UUID userId, String email);
    String generateRefreshToken(UUID userId);
    boolean validateToken(String token);
    UUID getUserIdFromToken(String token);
}
