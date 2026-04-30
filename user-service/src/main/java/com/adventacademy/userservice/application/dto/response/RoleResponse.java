package com.adventacademy.userservice.application.dto.response;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

public record RoleResponse(
        UUID id,
        String name,
        String description,
        Set<String> permissions,
        boolean systemRole,
        LocalDateTime createdAt
) {}
