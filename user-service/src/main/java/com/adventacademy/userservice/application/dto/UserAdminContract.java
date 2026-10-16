package com.adventacademy.userservice.application.dto;

import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class UserAdminContract {
    private UserAdminContract() {}

    public record Create(
            @NotNull UUID requestId,
            @NotBlank @Size(max = 50) String firstName,
            @NotBlank @Size(max = 50) String lastName,
            @NotBlank @Email @Size(max = 255) String email,
            @NotNull
                    Set<@Pattern(regexp = "ADMIN|CONTENT_EDITOR|INSTRUCTOR|STUDENT") String>
                            roles) {}

    public record Roles(
            @NotNull @Min(0) Long version,
            @NotNull
                    Set<@Pattern(regexp = "ADMIN|CONTENT_EDITOR|INSTRUCTOR|STUDENT") String>
                            roles) {}

    public record Version(@NotNull @Min(0) Long version) {}

    public record Resend(@NotNull UUID requestId) {}

    public record Accept(
            @NotBlank @Size(max = 100) String token,
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotBlank String confirmPassword) {}

    public record Invitation(
            String status, Instant expiresAt, Instant sentAt, Instant canResendAt) {}

    public record User(
            UUID id,
            String firstName,
            String lastName,
            String email,
            List<String> roles,
            String status,
            boolean activated,
            boolean emailVerified,
            boolean temporarilyLocked,
            Instant lockedUntil,
            Instant createdAt,
            long version,
            Invitation invitation,
            boolean canRemoveAdmin,
            boolean canBlock,
            boolean canUnblock,
            boolean canUnlock) {}

    public record Page<T>(List<T> items, long total, int page, int size) {}

    public record Event(
            UUID id,
            UUID userId,
            String actorName,
            String action,
            String beforeValue,
            String afterValue,
            Instant createdAt) {}
}
