package com.adventacademy.userservice.infrastructure.workflow;

import com.adventacademy.userservice.application.service.role.RoleRequestService;
import com.adventacademy.userservice.domain.exception.InvalidRoleRequestStateException;
import com.adventacademy.userservice.domain.model.RoleRequestStatus;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.worker.JobClient;
import io.camunda.zeebe.spring.client.annotation.JobWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Handles the {@code role-request-reject} service task: flips the {@code RoleRequest}
 * to {@code REJECTED} without ever touching the user's roles. Idempotent: a
 * redelivered job for a request already REJECTED completes as a no-op; any
 * other non-PENDING state fails instead of silently doing nothing.
 * <p>
 * Same permanent-vs-transient failure handling as {@link ApproveRoleRequestWorker}
 * — see {@link PermanentWorkflowFailure}.
 */
@Component
public class RejectRoleRequestWorker {

    private static final Logger logger = LoggerFactory.getLogger(RejectRoleRequestWorker.class);

    private final RoleRequestService roleRequestService;

    public RejectRoleRequestWorker(RoleRequestService roleRequestService) {
        this.roleRequestService = roleRequestService;
    }

    @JobWorker(type = "role-request-reject", autoComplete = false)
    public void handle(JobClient client, ActivatedJob job) {
        try {
            Map<String, Object> variables = job.getVariablesAsMap();
            UUID roleRequestId = UUID.fromString((String) variables.get("roleRequestId"));
            UUID reviewerId = UUID.fromString((String) variables.get("reviewerId"));
            String reason = (String) variables.get("reason");

            RoleRequestStatus status = roleRequestService.currentStatus(roleRequestId);
            if (status == RoleRequestStatus.REJECTED) {
                client.newCompleteCommand(job.getKey()).send().join();
                return;
            }
            if (status != RoleRequestStatus.PENDING) {
                throw new InvalidRoleRequestStateException(
                        String.format("Role request %s is %s, cannot reject", roleRequestId, status));
            }

            roleRequestService.markRejected(roleRequestId, reviewerId, reason == null || reason.isBlank() ? null : reason);
            client.newCompleteCommand(job.getKey()).send().join();
        } catch (Exception e) {
            handleFailure(client, job, e);
        }
    }

    private void handleFailure(JobClient client, ActivatedJob job, Exception e) {
        String roleRequestId = String.valueOf(job.getVariablesAsMap().get("roleRequestId"));

        if (PermanentWorkflowFailure.isPermanent(e) || job.getRetries() <= 1) {
            logger.error("Role request {} rejection failed terminally: {}", roleRequestId, e.getMessage(), e);
            markFailedIfIdentifiable(roleRequestId, e.getMessage());
            client.newFailCommand(job.getKey())
                    .retries(0)
                    .errorMessage(e.getMessage())
                    .send()
                    .join();
        } else {
            logger.warn("Role request {} rejection attempt failed, {} retries left: {}",
                    roleRequestId, job.getRetries() - 1, e.getMessage());
            client.newFailCommand(job.getKey())
                    .retries(job.getRetries() - 1)
                    .errorMessage(e.getMessage())
                    .send()
                    .join();
        }
    }

    /**
     * See {@link ApproveRoleRequestWorker#markFailedIfIdentifiable} for why this
     * can't always mark the request FAILED.
     */
    private void markFailedIfIdentifiable(String roleRequestId, String reason) {
        try {
            roleRequestService.markFailed(UUID.fromString(roleRequestId), reason);
        } catch (IllegalArgumentException | NullPointerException idUnusable) {
            logger.error("Cannot mark role request FAILED, id itself is unusable: {}", roleRequestId);
        } catch (InvalidRoleRequestStateException alreadySettled) {
            logger.warn("Role request {} is already settled in another terminal state, "
                    + "not overwriting with FAILED: {}", roleRequestId, alreadySettled.getMessage());
        }
    }
}
