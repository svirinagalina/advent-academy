package com.adventacademy.userservice.application.dto.response;

import com.adventacademy.userservice.domain.model.UserStatus;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String firstName,
        String lastName,
        UserStatus status,
        Set<String> roles,
        boolean emailVerified,
        boolean mfaEnabled,
        LocalDateTime lastLoginAt,
        LocalDateTime createdAt
) {}
