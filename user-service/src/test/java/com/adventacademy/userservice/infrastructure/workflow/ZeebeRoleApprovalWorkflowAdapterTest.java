package com.adventacademy.userservice.infrastructure.workflow;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.ZeebeFuture;
import io.camunda.zeebe.client.api.command.CompleteUserTaskCommandStep1;
import io.camunda.zeebe.client.api.response.CompleteUserTaskResponse;
import io.camunda.zeebe.client.api.search.filter.UserTaskFilter;
import io.camunda.zeebe.client.api.search.query.UserTaskQuery;
import io.camunda.zeebe.client.api.search.response.SearchQueryResponse;
import io.camunda.zeebe.client.api.search.response.UserTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit coverage for the one leg of the workflow adapter that the project's
 * lightweight {@code @ZeebeSpringTest} engine can't exercise live (see
 * {@link RoleApprovalWorkflowIT} for why) — proves {@code submitDecision}
 * queries by process instance, completes the right user task with the right
 * variables, and fails loudly when no review task is pending.
 */
@ExtendWith(MockitoExtension.class)
class ZeebeRoleApprovalWorkflowAdapterTest {

    @Mock private ZeebeClient zeebeClient;
    @Mock private UserTaskQuery userTaskQuery;
    @Mock private UserTask userTask;

    private ZeebeRoleApprovalWorkflowAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new ZeebeRoleApprovalWorkflowAdapter(zeebeClient);
        when(zeebeClient.newUserTaskQuery()).thenReturn(userTaskQuery);
        when(userTaskQuery.filter(org.mockito.ArgumentMatchers.<Consumer<UserTaskFilter>>any()))
                .thenReturn(userTaskQuery);
    }

    @SuppressWarnings("unchecked")
    private void stubQueryResult(List<UserTask> tasks) {
        ZeebeFuture<SearchQueryResponse<UserTask>> future = mock(ZeebeFuture.class);
        SearchQueryResponse<UserTask> response = mock(SearchQueryResponse.class);
        when(response.items()).thenReturn(tasks);
        when(future.join()).thenReturn(response);
        when(userTaskQuery.send()).thenReturn(future);
    }

    @Test
    void submitDecisionFiltersByProcessInstanceKey() {
        stubQueryResult(List.of(userTask));
        when(userTask.getKey()).thenReturn(999L);
        stubCompleteCommand(999L);

        adapter.submitDecision(4242L, "APPROVE", UUID.randomUUID(), null);

        ArgumentCaptor<Consumer<UserTaskFilter>> filterCaptor = ArgumentCaptor.forClass(Consumer.class);
        verify(userTaskQuery).filter(filterCaptor.capture());
        UserTaskFilter filter = mock(UserTaskFilter.class);
        when(filter.processInstanceKey(any())).thenReturn(filter);
        filterCaptor.getValue().accept(filter);
        verify(filter).processInstanceKey(4242L);
    }

    @Test
    void submitDecisionCompletesTheFoundUserTaskWithDecisionVariables() {
        stubQueryResult(List.of(userTask));
        when(userTask.getKey()).thenReturn(999L);
        CompleteUserTaskCommandStep1 completeCommand = stubCompleteCommand(999L);

        UUID reviewerId = UUID.randomUUID();
        adapter.submitDecision(4242L, "REJECT", reviewerId, "Not needed");

        verify(completeCommand).variables(Map.of(
                "decision", "REJECT",
                "reviewerId", reviewerId.toString(),
                "reason", "Not needed"));
    }

    @Test
    void submitDecisionNormalizesNullReasonToEmptyString() {
        stubQueryResult(List.of(userTask));
        when(userTask.getKey()).thenReturn(999L);
        CompleteUserTaskCommandStep1 completeCommand = stubCompleteCommand(999L);

        UUID reviewerId = UUID.randomUUID();
        adapter.submitDecision(4242L, "APPROVE", reviewerId, null);

        verify(completeCommand).variables(Map.of(
                "decision", "APPROVE",
                "reviewerId", reviewerId.toString(),
                "reason", ""));
    }

    @Test
    void submitDecisionThrowsWhenNoReviewTaskIsPending() {
        stubQueryResult(List.of());

        assertThatThrownBy(() -> adapter.submitDecision(4242L, "APPROVE", UUID.randomUUID(), null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("4242");
    }

    @SuppressWarnings("unchecked")
    private CompleteUserTaskCommandStep1 stubCompleteCommand(long userTaskKey) {
        CompleteUserTaskCommandStep1 command = mock(CompleteUserTaskCommandStep1.class);
        ZeebeFuture<CompleteUserTaskResponse> future = mock(ZeebeFuture.class);
        when(zeebeClient.newUserTaskCompleteCommand(userTaskKey)).thenReturn(command);
        when(command.variables(org.mockito.ArgumentMatchers.<Map<String, Object>>any())).thenReturn(command);
        when(command.send()).thenReturn(future);
        return command;
    }
}
