package com.adventacademy.userservice.application.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record PermissionResponse(
        UUID id,
        String name,
        String description,
        LocalDateTime createdAt
) {}
