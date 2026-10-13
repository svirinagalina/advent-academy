package com.adventacademy.userservice.infrastructure.workflow;

import com.adventacademy.userservice.application.port.AuditTrailPort;
import com.adventacademy.userservice.application.service.role.RoleRequestService;
import com.adventacademy.userservice.domain.model.RoleRequestStatus;
import com.adventacademy.userservice.domain.model.UserStatus;
import com.adventacademy.userservice.domain.repository.UserRepository;
import com.adventacademy.userservice.infrastructure.persistence.entity.RoleEntity;
import com.adventacademy.userservice.infrastructure.persistence.entity.UserEntity;
import com.adventacademy.userservice.infrastructure.persistence.jpa.RoleJpaRepository;
import com.adventacademy.userservice.infrastructure.persistence.jpa.RoleRequestJpaRepository;
import com.adventacademy.userservice.infrastructure.persistence.jpa.UserJpaRepository;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.worker.JobClient;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Exercises the real {@link com.adventacademy.userservice.application.service.role.AssignRoleService}
 * and {@link RoleRequestService} (real JPA, real H2) through the real worker
 * classes, feeding each worker a hand-built job instead of a live broker
 * delivering one.
 * <p>
 * This is deliberate, not a workaround: the part of the workflow this issue is
 * actually about — idempotent role assignment, idempotent status transitions,
 * permanent-vs-transient failure classification — lives entirely in our own
 * code, downstream of whatever delivered the job. The part that genuinely needs
 * a live Camunda broker (BPMN gateway routing, the REST-only user-task
 * completion commands) is already covered by {@link RoleApprovalWorkflowIT} and
 * {@link ZeebeRoleApprovalWorkflowAdapterTest} respectively, per the constraint
 * documented on {@code RoleApprovalWorkflowIT}.
 */
@SpringBootTest
@ActiveProfiles("test")
// V2__seed_default_roles.sql uses Postgres-only "ON CONFLICT ... DO NOTHING"; ddl-auto:
// create-drop already builds the schema this test needs from the entity mappings, and
// every fixture below is seeded directly through the JPA repositories anyway.
@TestPropertySource(properties = "spring.flyway.enabled=false")
class RoleApprovalWorkerIntegrationIT {

    @Autowired private RoleRequestService roleRequestService;
    @Autowired private ApproveRoleRequestWorker approveWorker;
    @Autowired private RejectRoleRequestWorker rejectWorker;
    @Autowired private UserJpaRepository userJpaRepository;
    @Autowired private RoleJpaRepository roleJpaRepository;
    @Autowired private RoleRequestJpaRepository roleRequestJpaRepository;
    @Autowired private UserRepository userRepository;

    // audit_events uses a Postgres-only "jsonb" columnDefinition that H2 can't create;
    // see RoleApprovalWorkflowIT for the full explanation.
    @MockitoBean private AuditTrailPort auditTrailPort;

    @Test
    void submitThenApproveAssignsRoleAndMarksApproved() {
        UUID userId = seedUser();
        UUID roleId = seedRole();
        UUID reviewerId = UUID.randomUUID();
        UUID roleRequestId = roleRequestService.submit(userId, roleId, "127.0.0.1", "JUnit").id();

        approveWorker.handle(mockClient(), approveJob(roleRequestId, userId, roleId, reviewerId));

        assertThat(roleRequestJpaRepository.findById(roleRequestId).orElseThrow().getStatus())
                .isEqualTo(RoleRequestStatus.APPROVED);
        assertThat(userRepository.findByIdWithRoles(userId).orElseThrow().getRoles())
                .anyMatch(r -> r.getId().equals(roleId));
    }

    @Test
    void submitThenRejectDoesNotAssignRoleAndMarksRejected() {
        UUID userId = seedUser();
        UUID roleId = seedRole();
        UUID reviewerId = UUID.randomUUID();
        UUID roleRequestId = roleRequestService.submit(userId, roleId, "127.0.0.1", "JUnit").id();

        rejectWorker.handle(mockClient(), rejectJob(roleRequestId, reviewerId, "Not needed"));

        assertThat(roleRequestJpaRepository.findById(roleRequestId).orElseThrow().getStatus())
                .isEqualTo(RoleRequestStatus.REJECTED);
        assertThat(userRepository.findByIdWithRoles(userId).orElseThrow().getRoles())
                .noneMatch(r -> r.getId().equals(roleId));
    }

    @Test
    void redeliveredApproveJobIsIdempotent() {
        UUID userId = seedUser();
        UUID roleId = seedRole();
        UUID reviewerId = UUID.randomUUID();
        UUID roleRequestId = roleRequestService.submit(userId, roleId, "127.0.0.1", "JUnit").id();
        ActivatedJob job = approveJob(roleRequestId, userId, roleId, reviewerId);

        approveWorker.handle(mockClient(), job);
        // Same job redelivered by Zeebe (ack lost, worker restart, ...) — must not throw,
        // must not double-insert into user_roles, must not misclassify as a failure.
        approveWorker.handle(mockClient(), job);

        assertThat(roleRequestJpaRepository.findById(roleRequestId).orElseThrow().getStatus())
                .isEqualTo(RoleRequestStatus.APPROVED);
        assertThat(userRepository.findByIdWithRoles(userId).orElseThrow().getRoles())
                .filteredOn(r -> r.getId().equals(roleId))
                .hasSize(1);
    }

    @Test
    void redeliveredRejectJobIsIdempotent() {
        UUID userId = seedUser();
        UUID roleId = seedRole();
        UUID reviewerId = UUID.randomUUID();
        UUID roleRequestId = roleRequestService.submit(userId, roleId, "127.0.0.1", "JUnit").id();
        ActivatedJob job = rejectJob(roleRequestId, reviewerId, "Not needed");

        rejectWorker.handle(mockClient(), job);
        rejectWorker.handle(mockClient(), job);

        assertThat(roleRequestJpaRepository.findById(roleRequestId).orElseThrow().getStatus())
                .isEqualTo(RoleRequestStatus.REJECTED);
    }

    @Test
    void missingRoleFailsTheJobImmediatelyAndMarksRequestFailed() {
        UUID userId = seedUser();
        UUID roleId = seedRole();
        UUID reviewerId = UUID.randomUUID();
        UUID roleRequestId = roleRequestService.submit(userId, roleId, "127.0.0.1", "JUnit").id();
        UUID nonExistentRoleId = UUID.randomUUID();
        JobClient client = mockClient();
        ActivatedJob job = approveJob(roleRequestId, userId, nonExistentRoleId, reviewerId);
        when(job.getRetries()).thenReturn(5);

        approveWorker.handle(client, job);

        assertThat(roleRequestJpaRepository.findById(roleRequestId).orElseThrow().getStatus())
                .isEqualTo(RoleRequestStatus.FAILED);
        verify(client.newFailCommand(job.getKey()), times(1)).retries(0);
    }

    @Test
    void approveWorkerOnAlreadyRejectedRequestFailsTerminallyInsteadOfSilentlyNoOp() {
        UUID userId = seedUser();
        UUID roleId = seedRole();
        UUID reviewerId = UUID.randomUUID();
        UUID roleRequestId = roleRequestService.submit(userId, roleId, "127.0.0.1", "JUnit").id();
        rejectWorker.handle(mockClient(), rejectJob(roleRequestId, reviewerId, "Not needed"));

        approveWorker.handle(mockClient(), approveJob(roleRequestId, userId, roleId, reviewerId));

        assertThat(roleRequestJpaRepository.findById(roleRequestId).orElseThrow().getStatus())
                .isEqualTo(RoleRequestStatus.REJECTED)
                .isNotEqualTo(RoleRequestStatus.APPROVED);
        assertThat(userRepository.findByIdWithRoles(userId).orElseThrow().getRoles())
                .noneMatch(r -> r.getId().equals(roleId));
    }

    private static JobClient mockClient() {
        return mock(JobClient.class, Answers.RETURNS_DEEP_STUBS);
    }

    private static ActivatedJob approveJob(UUID roleRequestId, UUID userId, UUID roleId, UUID reviewerId) {
        ActivatedJob job = mock(ActivatedJob.class);
        when(job.getKey()).thenReturn(1001L);
        when(job.getRetries()).thenReturn(3);
        when(job.getVariablesAsMap()).thenReturn(Map.of(
                "roleRequestId", roleRequestId.toString(),
                "userId", userId.toString(),
                "requestedRoleId", roleId.toString(),
                "reviewerId", reviewerId.toString()));
        return job;
    }

    private static ActivatedJob rejectJob(UUID roleRequestId, UUID reviewerId, String reason) {
        ActivatedJob job = mock(ActivatedJob.class);
        when(job.getKey()).thenReturn(2002L);
        when(job.getRetries()).thenReturn(3);
        when(job.getVariablesAsMap()).thenReturn(Map.of(
                "roleRequestId", roleRequestId.toString(),
                "reviewerId", reviewerId.toString(),
                "reason", reason));
        return job;
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
        role.setDescription("Workflow hardening test role");
        return roleJpaRepository.save(role).getId();
    }
}
