package com.adventacademy.userservice.application.dto.request;

import jakarta.validation.constraints.NotBlank;

public record RefreshTokenRequest(
        @NotBlank(message = "Refresh token is required")
        String refreshToken,
        String ipAddress,

        String userAgent
) {}
