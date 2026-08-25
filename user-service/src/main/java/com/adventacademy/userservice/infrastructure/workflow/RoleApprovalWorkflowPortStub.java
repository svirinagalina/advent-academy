package com.adventacademy.userservice.infrastructure.workflow;

import com.adventacademy.userservice.application.port.RoleApprovalWorkflowPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Fallback used whenever {@code camunda.client.zeebe.enabled} is false (the plain
 * {@code test} profile, most local/CI runs without a broker). Submitting a role
 * request must still succeed here — {@code RoleRequestService.submit} doesn't stop
 * working just because this environment has no Camunda connection — it simply
 * leaves {@code processInstanceKey} unset, which {@link
 * com.adventacademy.userservice.domain.model.RoleRequest#requireDecidable()}
 * already treats as "not decidable", so nothing downstream is misled into thinking
 * a real process is running.
 */
@Component
@ConditionalOnProperty(prefix = "camunda.client.zeebe", name = "enabled", havingValue = "false", matchIfMissing = true)
public class RoleApprovalWorkflowPortStub implements RoleApprovalWorkflowPort {

    @Override
    public Long start(UUID roleRequestId, UUID userId, UUID requestedRoleId) {
        return null;
    }

    @Override
    public void submitDecision(long processInstanceKey, String decision, UUID reviewerId, String reason) {
        throw new UnsupportedOperationException(
                "Camunda is disabled in this profile (camunda.client.zeebe.enabled=false); "
                        + "role request decisions require the local/docker/camunda-process-test profile");
    }
}
