package com.adventacademy.userservice.infrastructure.workflow;

import com.adventacademy.userservice.application.port.RoleApprovalWorkflowPort;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.search.response.UserTask;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Drives the {@code role-approval} BPMN process through the Zeebe client, keeping
 * every Camunda-specific type out of the application layer. Active only where a
 * real broker connection is configured; see {@link RoleApprovalWorkflowPortStub}
 * for the fallback used when {@code camunda.client.zeebe.enabled} is false.
 */
@Component
@ConditionalOnProperty(prefix = "camunda.client.zeebe", name = "enabled", havingValue = "true")
public class ZeebeRoleApprovalWorkflowAdapter implements RoleApprovalWorkflowPort {

    private static final String PROCESS_ID = "role-approval";

    private final ZeebeClient zeebeClient;

    public ZeebeRoleApprovalWorkflowAdapter(ZeebeClient zeebeClient) {
        this.zeebeClient = zeebeClient;
    }

    @Override
    public Long start(UUID roleRequestId, UUID userId, UUID requestedRoleId) {
        var processInstance = zeebeClient.newCreateInstanceCommand()
                .bpmnProcessId(PROCESS_ID)
                .latestVersion()
                .variables(Map.of(
                        "roleRequestId", roleRequestId.toString(),
                        "userId", userId.toString(),
                        "requestedRoleId", requestedRoleId.toString()))
                .send()
                .join();
        return processInstance.getProcessInstanceKey();
    }

    @Override
    public void submitDecision(long processInstanceKey, String decision, UUID reviewerId, String reason) {
        List<UserTask> tasks = zeebeClient.newUserTaskQuery()
                .filter(f -> f.processInstanceKey(processInstanceKey))
                .send()
                .join()
                .items();

        if (tasks.isEmpty()) {
            throw new IllegalStateException(
                    "No review user task found for process instance " + processInstanceKey);
        }

        long userTaskKey = tasks.get(0).getKey();
        zeebeClient.newUserTaskCompleteCommand(userTaskKey)
                .variables(Map.of(
                        "decision", decision,
                        "reviewerId", reviewerId.toString(),
                        "reason", reason == null ? "" : reason))
                .send()
                .join();
    }
}
