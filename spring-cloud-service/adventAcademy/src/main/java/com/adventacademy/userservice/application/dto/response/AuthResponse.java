package com.adventacademy.userservice.application.dto.response;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        long expiresIn,
        boolean mfaRequired
) {}
