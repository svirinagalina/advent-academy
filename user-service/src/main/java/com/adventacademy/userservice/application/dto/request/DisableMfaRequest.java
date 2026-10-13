package com.adventacademy.userservice.application.dto.request;

import jakarta.validation.constraints.NotBlank;

/** Step-up: disabling MFA requires the current password and a current TOTP or recovery code. */
public record DisableMfaRequest(
        @NotBlank(message = "Password is required")
        String password,
        @NotBlank(message = "Code is required")
        String code
) {}
