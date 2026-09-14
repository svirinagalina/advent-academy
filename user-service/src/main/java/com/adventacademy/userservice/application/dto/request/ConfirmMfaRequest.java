package com.adventacademy.userservice.application.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ConfirmMfaRequest(
        @NotBlank(message = "Code is required")
        String code
) {}
