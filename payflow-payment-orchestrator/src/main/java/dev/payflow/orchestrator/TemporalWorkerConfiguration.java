package dev.payflow.orchestrator;

import dev.payflow.orchestrator.activity.PaymentActivities;
import dev.payflow.orchestrator.workflow.PaymentWorkflowImpl;
import io.temporal.client.WorkflowClient;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TemporalWorkerConfiguration {

  public static final String PAYMENT_TASK_QUEUE = "PAYMENT_TASK_QUEUE";

  @Bean
  public WorkflowServiceStubs workflowServiceStubs(@Value("${temporal.target}") String target) {
    // Health check disabled: stub creation would otherwise block and fail synchronously (and
    // fail Spring context startup) whenever this service starts before the Temporal server is
    // reachable. Connection and polling retry in the background either way.
    return WorkflowServiceStubs.newServiceStubs(
        WorkflowServiceStubsOptions.newBuilder().setTarget(target).setDisableHealthCheck(true).build());
  }

  @Bean
  public WorkflowClient workflowClient(WorkflowServiceStubs workflowServiceStubs) {
    return WorkflowClient.newInstance(workflowServiceStubs);
  }

  @Bean
  public WorkerFactory workerFactory(WorkflowClient workflowClient, PaymentActivities paymentActivities) {
    WorkerFactory factory = WorkerFactory.newInstance(workflowClient);
    Worker worker = factory.newWorker(PAYMENT_TASK_QUEUE);
    worker.registerWorkflowImplementationTypes(PaymentWorkflowImpl.class);
    worker.registerActivitiesImplementations(paymentActivities);
    return factory;
  }
}
