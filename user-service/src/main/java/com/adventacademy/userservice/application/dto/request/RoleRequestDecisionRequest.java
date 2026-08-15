package com.adventacademy.userservice.application.dto.request;

import com.adventacademy.userservice.domain.model.RoleRequestDecision;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@Schema(description = "Admin decision on a pending role approval request")
public record RoleRequestDecisionRequest(
        @Schema(description = "APPROVE grants the requested role, REJECT leaves roles unchanged", example = "APPROVE")
        @NotNull RoleRequestDecision decision,

        @Schema(description = "Admin making the decision", example = "44444444-4444-4444-4444-444444444444")
        @NotNull UUID reviewerId,

        @Schema(description = "Optional explanation, most useful for REJECT", example = "Role is not required for this user", nullable = true)
        @Size(max = 500) String reason
) {
}
