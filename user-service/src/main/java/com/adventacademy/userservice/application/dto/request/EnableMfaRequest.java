package com.adventacademy.userservice.application.dto.request;

import jakarta.validation.constraints.NotBlank;

/** Step-up: enabling MFA requires the current password, not just a valid access token. */
public record EnableMfaRequest(
        @NotBlank(message = "Password is required")
        String password
) {}
