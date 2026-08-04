package com.adventacademy.userservice.infrastructure.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.client.ZeebeClient;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@Tag("camunda-smoke")
@ActiveProfiles("local")
@SpringBootTest(properties = "spring.main.web-application-type=none")
class CamundaConnectionSmokeTest {

    @Autowired
    private ZeebeClient zeebeClient;

    @Test
    void applicationConnectsToZeebeBroker() {
        var topology = zeebeClient.newTopologyRequest().send().join();
        assertThat(topology.getBrokers()).isNotEmpty();

        var processInstance = zeebeClient.newCreateInstanceCommand()
                .bpmnProcessId("role-approval")
                .latestVersion()
                .variables(Map.of(
                        "userId", "smoke-test-user",
                        "requestedRole", "MENTOR"))
                .send()
                .join();

        try {
            assertThat(processInstance.getBpmnProcessId()).isEqualTo("role-approval");
            assertThat(processInstance.getProcessInstanceKey()).isPositive();
        } finally {
            zeebeClient.newCancelInstanceCommand(processInstance.getProcessInstanceKey())
                    .send()
                    .join();
        }
    }
}
