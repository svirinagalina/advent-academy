package com.adventacademy.userservice.infrastructure.workflow;

import com.adventacademy.userservice.application.service.role.RoleRequestService;
import com.adventacademy.userservice.domain.exception.InvalidRoleRequestStateException;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.worker.JobClient;
import io.camunda.zeebe.spring.client.annotation.JobWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Handles the {@code role-request-approve} service task. Delegates entirely to
 * {@link RoleRequestService#approve}, which assigns the role and flips the
 * {@code RoleRequest} to {@code APPROVED} in a single transaction — this worker
 * calls that method and nothing else, so there is no window where one half of
 * that pair can succeed without the other (a redelivered Zeebe job is safe to
 * run again in full; {@code approve} itself is idempotent).
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

    private final RoleRequestService roleRequestService;

    public ApproveRoleRequestWorker(RoleRequestService roleRequestService) {
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

            roleRequestService.approve(roleRequestId, userId, requestedRoleId, reviewerId);
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
