package com.adventacademy.userservice.application.dto.response;

import com.adventacademy.userservice.domain.model.RoleRequestStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "Role approval request state")
public record RoleRequestResponse(
        @Schema(example = "22222222-2222-2222-2222-222222222222")
        UUID id,
        @Schema(example = "33333333-3333-3333-3333-333333333333")
        UUID userId,
        @Schema(example = "11111111-1111-1111-1111-111111111111")
        UUID requestedRoleId,
        @Schema(example = "PENDING")
        RoleRequestStatus status,
        @Schema(example = "44444444-4444-4444-4444-444444444444", nullable = true)
        UUID reviewerId,
        @Schema(example = "Role is not required for this user", nullable = true)
        String decisionReason,
        @Schema(example = "2026-07-22T10:00:00")
        LocalDateTime createdAt,
        @Schema(example = "2026-07-22T11:00:00", nullable = true)
        LocalDateTime reviewedAt,
        @Schema(example = "2026-07-22T10:00:00")
        LocalDateTime updatedAt
) {
}
