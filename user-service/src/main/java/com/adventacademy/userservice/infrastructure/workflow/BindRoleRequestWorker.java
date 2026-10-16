package com.adventacademy.userservice.infrastructure.workflow;

import com.adventacademy.userservice.application.service.role.WorkflowStartLease;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.worker.JobClient;
import io.camunda.zeebe.spring.client.annotation.JobWorker;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** A lost create-instance response may produce another process; only one instance can reach human review. */
@Component
public class BindRoleRequestWorker {
    private final WorkflowStartLease lease;
    public BindRoleRequestWorker(WorkflowStartLease lease) { this.lease = lease; }

    @JobWorker(type = "role-request-bind", autoComplete = false)
    public void bind(JobClient client, ActivatedJob job) {
        UUID request = UUID.fromString((String) job.getVariablesAsMap().get("roleRequestId"));
        boolean canonical = lease.bind(request, job.getProcessInstanceKey());
        client.newCompleteCommand(job.getKey()).variables(Map.of("workflowCanonical", canonical)).send().join();
    }
}
