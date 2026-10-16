package com.adventacademy.userservice.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CompletePasswordResetRequest(
        @NotBlank(message = "Token is required")
        String token,
        @NotBlank(message = "New password is required")
        @Size(min = 8, max = 72, message = "New password must be between 8 and 72 characters")
        String newPassword,
        @NotBlank(message = "Password confirmation is required")
        String confirmPassword,
        String ipAddress,
        String userAgent
) {}
