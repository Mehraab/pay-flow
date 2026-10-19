package dev.payflow.paymentcommand;

import io.temporal.client.WorkflowClient;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TemporalClientConfiguration {

  @Bean
  public WorkflowServiceStubs workflowServiceStubs(@Value("${temporal.target}") String target) {
    // Health check disabled: a client-only stub shouldn't fail to construct (and so fail
    // Spring context startup) just because Temporal isn't reachable yet — the same reasoning
    // as the orchestrator's identical bean.
    return WorkflowServiceStubs.newServiceStubs(
        WorkflowServiceStubsOptions.newBuilder().setTarget(target).setDisableHealthCheck(true).build());
  }

  @Bean
  public WorkflowClient workflowClient(WorkflowServiceStubs workflowServiceStubs) {
    return WorkflowClient.newInstance(workflowServiceStubs);
  }
}
