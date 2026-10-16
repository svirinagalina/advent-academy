package com.adventacademy.userservice.infrastructure.workflow;

import com.adventacademy.userservice.application.service.role.RoleRequestService;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "camunda.client.zeebe", name = "enabled", havingValue = "true")
public class WorkflowStartRecovery {
    private final JdbcTemplate jdbc;
    private final RoleRequestService requests;
    public WorkflowStartRecovery(JdbcTemplate jdbc, RoleRequestService requests) { this.jdbc = jdbc; this.requests = requests; }

    @Scheduled(fixedDelayString = "${workflow.start-recovery-ms:10000}")
    public void recover() {
        for (var row : jdbc.queryForList("SELECT id,user_id,requested_role_id FROM role_requests WHERE status='PENDING' "
                + "AND process_instance_key IS NULL AND workflow_next_attempt_at<=CURRENT_TIMESTAMP ORDER BY workflow_next_attempt_at,id LIMIT 25")) {
            requests.onRoleRequestSubmitted(new com.adventacademy.userservice.application.service.role.RoleRequestSubmittedEvent(
                    (UUID) row.get("id"), (UUID) row.get("user_id"), (UUID) row.get("requested_role_id")));
        }
    }
}
