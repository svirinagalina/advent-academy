package com.adventacademy.userservice.infrastructure.workflow;

import com.adventacademy.userservice.application.service.role.RoleRequestService;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.worker.JobClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RejectRoleRequestWorkerTest {

    @Mock private RoleRequestService roleRequestService;
    @Mock private ActivatedJob job;

    private JobClient client;
    private RejectRoleRequestWorker worker;

    private UUID roleRequestId;
    private UUID reviewerId;

    @BeforeEach
    void setUp() {
        client = mock(JobClient.class, Answers.RETURNS_DEEP_STUBS);
        worker = new RejectRoleRequestWorker(roleRequestService);

        roleRequestId = UUID.randomUUID();
        reviewerId = UUID.randomUUID();

        when(job.getKey()).thenReturn(777L);
    }

    private Map<String, Object> variablesWithReason(String reason) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("roleRequestId", roleRequestId.toString());
        variables.put("reviewerId", reviewerId.toString());
        variables.put("reason", reason);
        return variables;
    }

    @Test
    void onSuccessMarksRejectedAndCompletesJobWithoutTouchingRoles() {
        when(job.getVariablesAsMap()).thenReturn(variablesWithReason("Not needed"));

        worker.handle(client, job);

        verify(roleRequestService).markRejected(roleRequestId, reviewerId, "Not needed");
        verify(client).newCompleteCommand(777L);
        verify(roleRequestService, never()).markFailed(any(), anyString());
    }

    @Test
    void blankReasonIsNormalizedToNull() {
        when(job.getVariablesAsMap()).thenReturn(variablesWithReason("   "));

        worker.handle(client, job);

        verify(roleRequestService).markRejected(roleRequestId, reviewerId, null);
    }

    @Test
    void onNonTerminalFailureDecrementsRetriesWithoutMarkingFailed() {
        when(job.getVariablesAsMap()).thenReturn(variablesWithReason("Not needed"));
        when(job.getRetries()).thenReturn(3);
        doThrow(new RuntimeException("db hiccup"))
                .when(roleRequestService).markRejected(any(), any(), any());

        worker.handle(client, job);

        verify(roleRequestService, never()).markFailed(any(), anyString());
        verify(client.newFailCommand(777L)).retries(2);
        verify(client, never()).newCompleteCommand(777L);
    }

    @Test
    void onTerminalFailureMarksFailedAndFailsJobWithZeroRetries() {
        when(job.getVariablesAsMap()).thenReturn(variablesWithReason("Not needed"));
        when(job.getRetries()).thenReturn(1);
        doThrow(new RuntimeException("request already decided"))
                .when(roleRequestService).markRejected(any(), any(), any());

        worker.handle(client, job);

        verify(roleRequestService).markFailed(roleRequestId, "request already decided");
        verify(client.newFailCommand(777L)).retries(0);
    }
}
