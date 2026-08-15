package com.adventacademy.userservice.infrastructure.workflow;

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
 * Handles the {@code role-request-reject} service task: flips the {@code RoleRequest}
 * to {@code REJECTED} without ever touching the user's roles.
 * <p>
 * Same explicit retry-vs-terminal-failure handling as {@link ApproveRoleRequestWorker}.
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
        Map<String, Object> variables = job.getVariablesAsMap();
        UUID roleRequestId = UUID.fromString((String) variables.get("roleRequestId"));
        UUID reviewerId = UUID.fromString((String) variables.get("reviewerId"));
        String reason = (String) variables.get("reason");

        try {
            roleRequestService.markRejected(roleRequestId, reviewerId, reason == null || reason.isBlank() ? null : reason);
            client.newCompleteCommand(job.getKey()).send().join();
        } catch (Exception e) {
            handleFailure(client, job, roleRequestId, e);
        }
    }

    private void handleFailure(JobClient client, ActivatedJob job, UUID roleRequestId, Exception e) {
        if (job.getRetries() <= 1) {
            logger.error("Role request {} rejection failed terminally: {}", roleRequestId, e.getMessage(), e);
            roleRequestService.markFailed(roleRequestId, e.getMessage());
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
}
