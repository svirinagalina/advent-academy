package com.adventacademy.userservice.infrastructure.workflow;

import io.camunda.zeebe.spring.client.annotation.Deployment;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile({"local", "docker"})
@Deployment(resources = "classpath:/bpmn/role-approval.bpmn")
public class CamundaDeploymentConfiguration {
}
