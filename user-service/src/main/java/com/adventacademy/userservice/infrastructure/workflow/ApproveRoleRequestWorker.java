package com.adventacademy.userservice.infrastructure.workflow;

import com.adventacademy.userservice.application.service.role.AssignRoleService;
import com.adventacademy.userservice.application.service.role.RoleRequestService;
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
 * steps go through application services, never through repositories directly.
 * <p>
 * On failure, retries are decremented explicitly until the last one, at which
 * point the request is marked {@code FAILED} and the job is failed with zero
 * retries, producing a visible incident in Operate instead of an infinite retry
 * loop or a silent DB mismatch.
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
        Map<String, Object> variables = job.getVariablesAsMap();
        UUID roleRequestId = UUID.fromString((String) variables.get("roleRequestId"));
        UUID userId = UUID.fromString((String) variables.get("userId"));
        UUID requestedRoleId = UUID.fromString((String) variables.get("requestedRoleId"));
        UUID reviewerId = UUID.fromString((String) variables.get("reviewerId"));

        try {
            assignRoleService.assignRoleToUser(userId, requestedRoleId, null, "camunda-worker");
            roleRequestService.markApproved(roleRequestId, reviewerId);
            client.newCompleteCommand(job.getKey()).send().join();
        } catch (Exception e) {
            handleFailure(client, job, roleRequestId, e);
        }
    }

    private void handleFailure(JobClient client, ActivatedJob job, UUID roleRequestId, Exception e) {
        if (job.getRetries() <= 1) {
            logger.error("Role request {} approval failed terminally: {}", roleRequestId, e.getMessage(), e);
            roleRequestService.markFailed(roleRequestId, e.getMessage());
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
}
