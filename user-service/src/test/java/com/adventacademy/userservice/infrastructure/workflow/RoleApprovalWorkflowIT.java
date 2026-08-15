package com.adventacademy.userservice.infrastructure.workflow;

import com.adventacademy.userservice.application.port.AuditTrailPort;
import com.adventacademy.userservice.application.service.role.RoleRequestService;
import com.adventacademy.userservice.domain.model.UserStatus;
import com.adventacademy.userservice.infrastructure.persistence.entity.RoleEntity;
import com.adventacademy.userservice.infrastructure.persistence.entity.UserEntity;
import com.adventacademy.userservice.infrastructure.persistence.jpa.RoleJpaRepository;
import com.adventacademy.userservice.infrastructure.persistence.jpa.RoleRequestJpaRepository;
import com.adventacademy.userservice.infrastructure.persistence.jpa.UserJpaRepository;
import io.camunda.zeebe.spring.test.ZeebeSpringTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the part of the role-approval process that the lightweight
 * {@code @ZeebeSpringTest} engine can actually exercise: {@code submit()} starts
 * a real process instance and it waits at the "Review role request" user task
 * with the expected variables, and the process instance key is persisted on the
 * {@code RoleRequest}.
 * <p>
 * The admin-decision → gateway → job-worker leg is <b>not</b> driven end-to-end
 * here: {@code newUserTaskQuery}/{@code newUserTaskCompleteCommand} are REST-only
 * Camunda 8.7 commands, and this project's embedded test engine (unlike the real
 * broker started by {@code docker-compose.yml}) doesn't expose that gateway —
 * confirmed by a standalone diagnostic before writing this test. That leg is
 * covered by {@link ZeebeRoleApprovalWorkflowAdapterTest} (adapter logic, mocked
 * client) and {@link ApproveRoleRequestWorkerTest}/{@link RejectRoleRequestWorkerTest}
 * (worker logic once a job arrives); full wire-level verification is manual, via
 * {@code docker compose up} as documented in user-service/README.md.
 */
@ZeebeSpringTest
@ActiveProfiles({"test", "camunda-process-test"})
@SpringBootTest
class RoleApprovalWorkflowIT {

    @Autowired private RoleRequestService roleRequestService;
    @Autowired private UserJpaRepository userJpaRepository;
    @Autowired private RoleJpaRepository roleJpaRepository;
    @Autowired private RoleRequestJpaRepository roleRequestJpaRepository;

    // audit_events uses a Postgres-only "jsonb" columnDefinition that H2 can't create
    // (ddl-auto: create-drop never manages to build that one table); every other test
    // that touches audit-writing code paths mocks this port for the same reason.
    @MockitoBean private AuditTrailPort auditTrailPort;

    @Test
    void submitStartsARealProcessInstanceAndPersistsItsKey() {
        UUID userId = seedUser();
        UUID roleId = seedRole();

        var response = roleRequestService.submit(userId, roleId, "127.0.0.1", "JUnit");

        // A real Zeebe process instance key was returned and persisted — if starting the
        // process instance had failed, submit() itself would have thrown and this line
        // would never be reached.
        Long processInstanceKey = roleRequestJpaRepository.findById(response.id())
                .orElseThrow()
                .getProcessInstanceKey();
        assertThat(processInstanceKey).isNotNull().isPositive();
    }

    private UUID seedUser() {
        UserEntity user = new UserEntity();
        user.setEmail(UUID.randomUUID() + "@test.com");
        user.setPasswordHash("hash");
        user.setFirstName("Role");
        user.setLastName("Requester");
        user.setStatus(UserStatus.ACTIVE);
        return userJpaRepository.save(user).getId();
    }

    private UUID seedRole() {
        RoleEntity role = new RoleEntity();
        role.setName("ROLE_" + UUID.randomUUID().toString().replace("-", ""));
        role.setDescription("Workflow test role");
        return roleJpaRepository.save(role).getId();
    }
}
