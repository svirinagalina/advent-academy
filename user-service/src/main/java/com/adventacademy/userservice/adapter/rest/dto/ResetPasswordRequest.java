package com.adventacademy.userservice.adapter.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request to complete password reset flow.
 * User receives reset token via email, then submits new password.
 */
public record ResetPasswordRequest(
    @NotBlank(message = "token is required")
    String token,

    @NotBlank(message = "newPassword is required")
    @Size(min = 8, max = 128, message = "password must be between 8 and 128 characters")
    String newPassword,

    @NotBlank(message = "confirmPassword is required")
    String confirmPassword
) {}
