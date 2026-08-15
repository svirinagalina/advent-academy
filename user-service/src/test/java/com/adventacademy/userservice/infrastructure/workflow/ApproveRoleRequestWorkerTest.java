package com.adventacademy.userservice.infrastructure.workflow;

import com.adventacademy.userservice.application.service.role.AssignRoleService;
import com.adventacademy.userservice.application.service.role.RoleRequestService;
import com.adventacademy.userservice.domain.exception.RoleNotFoundException;
import com.adventacademy.userservice.domain.exception.UserNotFoundException;
import com.adventacademy.userservice.domain.model.RoleRequestStatus;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.worker.JobClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApproveRoleRequestWorkerTest {

    @Mock private AssignRoleService assignRoleService;
    @Mock private RoleRequestService roleRequestService;
    @Mock private ActivatedJob job;

    private JobClient client;
    private ApproveRoleRequestWorker worker;

    private UUID roleRequestId;
    private UUID userId;
    private UUID requestedRoleId;
    private UUID reviewerId;

    @BeforeEach
    void setUp() {
        client = mock(JobClient.class, Answers.RETURNS_DEEP_STUBS);
        worker = new ApproveRoleRequestWorker(assignRoleService, roleRequestService);

        roleRequestId = UUID.randomUUID();
        userId = UUID.randomUUID();
        requestedRoleId = UUID.randomUUID();
        reviewerId = UUID.randomUUID();

        when(job.getKey()).thenReturn(555L);
        when(job.getVariablesAsMap()).thenReturn(Map.of(
                "roleRequestId", roleRequestId.toString(),
                "userId", userId.toString(),
                "requestedRoleId", requestedRoleId.toString(),
                "reviewerId", reviewerId.toString()));
        // Not every test reaches this call (e.g. malformed-variables fails before it).
        lenient().when(roleRequestService.currentStatus(roleRequestId)).thenReturn(RoleRequestStatus.PENDING);
    }

    @Test
    void onSuccessAssignsRoleMarksApprovedAndCompletesJob() {
        worker.handle(client, job);

        verify(assignRoleService).assignRoleToUser(userId, requestedRoleId, null, "camunda-worker");
        verify(roleRequestService).markApproved(roleRequestId, reviewerId);
        verify(client).newCompleteCommand(555L);
        verify(roleRequestService, never()).markFailed(any(), anyString());
    }

    @Test
    void onNonTerminalFailureDecrementsRetriesWithoutMarkingFailed() {
        when(job.getRetries()).thenReturn(3);
        when(assignRoleService.assignRoleToUser(any(), any(), any(), anyString()))
                .thenThrow(new RuntimeException("db hiccup"));

        worker.handle(client, job);

        verify(roleRequestService, never()).markFailed(any(), anyString());
        verify(client.newFailCommand(555L)).retries(2);
        verify(client, never()).newCompleteCommand(555L);
    }

    @Test
    void onTerminalFailureMarksFailedAndFailsJobWithZeroRetries() {
        when(job.getRetries()).thenReturn(1);
        when(assignRoleService.assignRoleToUser(any(), any(), any(), anyString()))
                .thenThrow(new RuntimeException("role no longer exists"));

        worker.handle(client, job);

        verify(roleRequestService).markFailed(roleRequestId, "role no longer exists");
        verify(client.newFailCommand(555L)).retries(0);
    }

    @Test
    void onMissingUserFailsImmediatelyEvenWithRetriesRemaining() {
        // getRetries() deliberately unstubbed: a permanent failure must not even look at it.
        when(assignRoleService.assignRoleToUser(any(), any(), any(), anyString()))
                .thenThrow(new UserNotFoundException(userId));

        worker.handle(client, job);

        verify(roleRequestService).markFailed(roleRequestId, "User not found with id: " + userId);
        verify(client.newFailCommand(555L)).retries(0);
    }

    @Test
    void onMissingRoleFailsImmediatelyEvenWithRetriesRemaining() {
        when(assignRoleService.assignRoleToUser(any(), any(), any(), anyString()))
                .thenThrow(new RoleNotFoundException(requestedRoleId));

        worker.handle(client, job);

        verify(roleRequestService).markFailed(roleRequestId, "Role not found with id: " + requestedRoleId);
        verify(client.newFailCommand(555L)).retries(0);
    }

    @Test
    void redeliveredJobForAlreadyApprovedRequestCompletesWithoutReassigningRole() {
        when(roleRequestService.currentStatus(roleRequestId)).thenReturn(RoleRequestStatus.APPROVED);

        worker.handle(client, job);

        verify(assignRoleService, never()).assignRoleToUser(any(), any(), any(), anyString());
        verify(roleRequestService, never()).markApproved(any(), any());
        verify(client).newCompleteCommand(555L);
    }

    @Test
    void wrongTerminalStateFailsWithoutEverTouchingRoleAssignment() {
        when(roleRequestService.currentStatus(roleRequestId)).thenReturn(RoleRequestStatus.REJECTED);

        worker.handle(client, job);

        verify(assignRoleService, never()).assignRoleToUser(any(), any(), any(), anyString());
        verify(roleRequestService, never()).markApproved(any(), any());
        verify(roleRequestService).markFailed(any(), anyString());
        verify(client.newFailCommand(555L)).retries(0);
    }

    @Test
    void onMalformedJobVariablesFailsJobImmediatelyWithoutMarkingAnyRequestFailed() {
        when(job.getVariablesAsMap()).thenReturn(Map.of(
                "roleRequestId", "not-a-uuid",
                "userId", userId.toString(),
                "requestedRoleId", requestedRoleId.toString(),
                "reviewerId", reviewerId.toString()));

        worker.handle(client, job);

        verify(roleRequestService, never()).markFailed(any(), anyString());
        verify(client.newFailCommand(555L)).retries(0);
        verify(client, never()).newCompleteCommand(555L);
    }
}
