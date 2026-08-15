package com.adventacademy.userservice.infrastructure.workflow;

import com.adventacademy.userservice.application.service.role.AssignRoleService;
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
 * Handles the {@code role-request-approve} service task: assigns the requested
 * role to the user, then flips the {@code RoleRequest} to {@code APPROVED}. Both
 * steps go through application services, never through repositories directly,
 * and both are idempotent, so a redelivered job (Zeebe is at-least-once) is safe
 * to run again in full.
 * <p>
 * The request's current status is checked <em>before</em> touching
 * {@code AssignRoleService}: already {@code APPROVED} completes the job as a
 * no-op redelivery, any other non-PENDING state fails without ever granting a
 * role — a wrongly-routed job (should never happen given the single-path BPMN
 * gateway, but is a real anomaly if it ever does) must not leave a role granted
 * for a request that ends up {@code FAILED}.
 * <p>
 * On failure, a permanent problem (missing user/role/request, wrong request
 * state, malformed job variables — see {@link PermanentWorkflowFailure}) marks
 * the request {@code FAILED} immediately, since retrying can't change the
 * outcome. Anything else gets Zeebe's normal retry budget, decremented
 * explicitly, and only goes {@code FAILED} once exhausted — producing a visible
 * incident in Operate instead of an infinite retry loop or a silent DB mismatch.
 */
@Component
public class ApproveRoleRequestWorker {

    private static final Logger logger = LoggerFactory.getLogger(ApproveRoleRequestWorker.class);

    private final AssignRoleService assignRoleService;
    private final RoleRequestService roleRequestService;

    public ApproveRoleRequestWorker(AssignRoleService assignRoleService, RoleRequestService roleRequestService) {
        this.assignRoleService = assignRoleService;
        this.roleRequestService = roleRequestService;
    }

    @JobWorker(type = "role-request-approve", autoComplete = false)
    public void handle(JobClient client, ActivatedJob job) {
        try {
            Map<String, Object> variables = job.getVariablesAsMap();
            UUID roleRequestId = UUID.fromString((String) variables.get("roleRequestId"));
            UUID userId = UUID.fromString((String) variables.get("userId"));
            UUID requestedRoleId = UUID.fromString((String) variables.get("requestedRoleId"));
            UUID reviewerId = UUID.fromString((String) variables.get("reviewerId"));

            RoleRequestStatus status = roleRequestService.currentStatus(roleRequestId);
            if (status == RoleRequestStatus.APPROVED) {
                client.newCompleteCommand(job.getKey()).send().join();
                return;
            }
            if (status != RoleRequestStatus.PENDING) {
                throw new InvalidRoleRequestStateException(
                        String.format("Role request %s is %s, cannot approve", roleRequestId, status));
            }

            assignRoleService.assignRoleToUser(userId, requestedRoleId, null, "camunda-worker");
            roleRequestService.markApproved(roleRequestId, reviewerId);
            client.newCompleteCommand(job.getKey()).send().join();
        } catch (Exception e) {
            handleFailure(client, job, e);
        }
    }

    private void handleFailure(JobClient client, ActivatedJob job, Exception e) {
        String roleRequestId = String.valueOf(job.getVariablesAsMap().get("roleRequestId"));

        if (PermanentWorkflowFailure.isPermanent(e) || job.getRetries() <= 1) {
            logger.error("Role request {} approval failed terminally: {}", roleRequestId, e.getMessage(), e);
            markFailedIfIdentifiable(roleRequestId, e.getMessage());
            client.newFailCommand(job.getKey())
                    .retries(0)
                    .errorMessage(e.getMessage())
                    .send()
                    .join();
        } else {
            logger.warn("Role request {} approval attempt failed, {} retries left: {}",
                    roleRequestId, job.getRetries() - 1, e.getMessage());
            client.newFailCommand(job.getKey())
                    .retries(job.getRetries() - 1)
                    .errorMessage(e.getMessage())
                    .send()
                    .join();
        }
    }

    /**
     * Two reasons this can't always mark the request FAILED: roleRequestId itself
     * can be the malformed/missing piece (e.g. not a valid UUID), or the request
     * can already be settled in some other terminal state (APPROVED/REJECTED) —
     * the domain rightly refuses to clobber that with FAILED. Either way there is
     * nothing more to do here; the Zeebe job itself still gets failed
     * unconditionally by {@link #handleFailure}.
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
