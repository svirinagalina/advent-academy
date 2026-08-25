package com.adventacademy.userservice.infrastructure.workflow;

import com.adventacademy.userservice.application.service.role.RoleRequestService;
import com.adventacademy.userservice.domain.exception.InvalidRoleRequestStateException;
import com.adventacademy.userservice.domain.exception.RoleNotFoundException;
import com.adventacademy.userservice.domain.exception.UserNotFoundException;
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
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApproveRoleRequestWorkerTest {

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
        worker = new ApproveRoleRequestWorker(roleRequestService);

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
    }

    @Test
    void onSuccessDelegatesToApproveAndCompletesJob() {
        doNothing().when(roleRequestService).approve(roleRequestId, userId, requestedRoleId, reviewerId);

        worker.handle(client, job);

        verify(roleRequestService).approve(roleRequestId, userId, requestedRoleId, reviewerId);
        verify(client).newCompleteCommand(555L);
        verify(roleRequestService, never()).markFailed(any(), anyString());
    }

    @Test
    void onNonTerminalFailureDecrementsRetriesWithoutMarkingFailed() {
        when(job.getRetries()).thenReturn(3);
        doThrow(new RuntimeException("db hiccup"))
                .when(roleRequestService).approve(roleRequestId, userId, requestedRoleId, reviewerId);

        worker.handle(client, job);

        verify(roleRequestService, never()).markFailed(any(), anyString());
        verify(client.newFailCommand(555L)).retries(2);
        verify(client, never()).newCompleteCommand(555L);
    }

    @Test
    void onTerminalFailureMarksFailedAndFailsJobWithZeroRetries() {
        when(job.getRetries()).thenReturn(1);
        doThrow(new RuntimeException("role no longer exists"))
                .when(roleRequestService).approve(roleRequestId, userId, requestedRoleId, reviewerId);

        worker.handle(client, job);

        verify(roleRequestService).markFailed(roleRequestId, "role no longer exists");
        verify(client.newFailCommand(555L)).retries(0);
    }

    @Test
    void onMissingUserFailsImmediatelyEvenWithRetriesRemaining() {
        // getRetries() deliberately unstubbed: a permanent failure must not even look at it.
        doThrow(new UserNotFoundException(userId))
                .when(roleRequestService).approve(roleRequestId, userId, requestedRoleId, reviewerId);

        worker.handle(client, job);

        verify(roleRequestService).markFailed(roleRequestId, "User not found with id: " + userId);
        verify(client.newFailCommand(555L)).retries(0);
    }

    @Test
    void onMissingRoleFailsImmediatelyEvenWithRetriesRemaining() {
        doThrow(new RoleNotFoundException(requestedRoleId))
                .when(roleRequestService).approve(roleRequestId, userId, requestedRoleId, reviewerId);

        worker.handle(client, job);

        verify(roleRequestService).markFailed(roleRequestId, "Role not found with id: " + requestedRoleId);
        verify(client.newFailCommand(555L)).retries(0);
    }

    @Test
    void redeliveredJobForAlreadyApprovedRequestCompletesAsNoOp() {
        // approve() itself treats an already-APPROVED request as a silent no-op —
        // nothing more for the worker to check or branch on here.
        doNothing().when(roleRequestService).approve(roleRequestId, userId, requestedRoleId, reviewerId);

        worker.handle(client, job);

        verify(client).newCompleteCommand(555L);
        verify(roleRequestService, never()).markFailed(any(), anyString());
    }

    @Test
    void wrongTerminalStateFailsWithoutRetryAndMarksFailed() {
        // approve() surfaces this via the domain's own InvalidRoleRequestStateException,
        // classified permanent by PermanentWorkflowFailure — no role is ever granted.
        doThrow(new InvalidRoleRequestStateException("Role request " + roleRequestId + " is REJECTED and cannot be changed"))
                .when(roleRequestService).approve(roleRequestId, userId, requestedRoleId, reviewerId);

        worker.handle(client, job);

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
