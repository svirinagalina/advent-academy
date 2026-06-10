package com.adventacademy.userservice.application.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record PasswordResetRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        String email,

        String ipAddress,

        String userAgent
) {}
