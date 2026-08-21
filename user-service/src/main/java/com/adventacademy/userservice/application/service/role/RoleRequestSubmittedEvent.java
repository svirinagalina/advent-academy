package com.adventacademy.userservice.application.service.role;

import java.util.UUID;

/**
 * Published after a {@code RoleRequest} row commits, so the Camunda process is
 * only started once the request is durably persisted — see
 * {@link RoleRequestService#submit} and {@link RoleRequestService#onRoleRequestSubmitted}.
 */
public record RoleRequestSubmittedEvent(UUID roleRequestId, UUID userId, UUID requestedRoleId) {
}
