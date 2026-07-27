package com.adventacademy.userservice.application.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(description = "Request body for submitting a role approval request")
public record SubmitRoleRequestRequest(
        @Schema(
                description = "Role that the user asks to receive",
                example = "11111111-1111-1111-1111-111111111111"
        )
        @NotNull UUID requestedRoleId
) {
}
