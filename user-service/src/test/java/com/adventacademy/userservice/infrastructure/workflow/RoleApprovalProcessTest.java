package com.adventacademy.userservice.infrastructure.workflow;

import static io.camunda.zeebe.process.test.assertions.BpmnAssert.assertThat;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.spring.test.ZeebeSpringTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@ZeebeSpringTest
@ActiveProfiles({"test", "camunda-process-test"})
@SpringBootTest(properties = "spring.main.web-application-type=none")
class RoleApprovalProcessTest {

    @Autowired
    private ZeebeClient zeebeClient;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private BindRoleRequestWorker binder;

    @Test
    void deploysRoleApprovalAndWaitsForReview() {
        var processInstance = zeebeClient.newCreateInstanceCommand()
                .bpmnProcessId("role-approval")
                .latestVersion()
                .variables(Map.of(
                        "userId", "process-test-user",
                        "requestedRole", "MENTOR"))
                .send()
                .join();

        assertThat(processInstance).isWaitingExactlyAtElements("ServiceTask_bind_request");
        var jobs = zeebeClient.newActivateJobsCommand().jobType("role-request-bind").maxJobsToActivate(1).send().join().getJobs();
        zeebeClient.newCompleteCommand(jobs.getFirst().getKey()).variables(Map.of("workflowCanonical",true)).send().join();
        assertThat(processInstance)
                .isStarted()
                .isActive()
                .hasPassedElement("StartEvent_role_request")
                .isWaitingExactlyAtElements("UserTask_review_role")
                .hasVariableWithValue("userId", "process-test-user")
                .hasVariableWithValue("requestedRole", "MENTOR")
                .hasNoIncidents();
    }
}
