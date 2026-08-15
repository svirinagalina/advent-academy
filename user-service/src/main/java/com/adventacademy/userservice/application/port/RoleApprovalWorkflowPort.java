package com.adventacademy.userservice.application.port;

import java.util.UUID;

/**
 * Drives the {@code role-approval} Camunda process from the application layer,
 * without exposing any Zeebe/Camunda types outside the infrastructure adapter.
 */
public interface RoleApprovalWorkflowPort {

    /**
     * Starts a new process instance for a freshly created role request.
     *
     * @return the Zeebe process instance key to store on the {@code RoleRequest}, or
     *         {@code null} if this environment has no real Camunda connection (the
     *         request then simply has no attached process and can never be decided)
     */
    Long start(UUID roleRequestId, UUID userId, UUID requestedRoleId);

    /**
     * Completes the pending "Review role request" user task for the given process
     * instance with the admin's decision, letting the process gateway route to the
     * approve or reject service task.
     */
    void submitDecision(long processInstanceKey, String decision, UUID reviewerId, String reason);
}
