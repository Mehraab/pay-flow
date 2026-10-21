package dev.payflow.paymentcommand.workflow;

import dev.payflow.orchestrator.workflow.PaymentWorkflow;
import dev.payflow.orchestrator.workflow.PaymentWorkflowInput;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import org.springframework.stereotype.Component;

/**
 * The sole place this service starts {@code PaymentWorkflow} executions — isolated in its own
 * class (rather than inlined in {@code CreatePaymentHandler}) so tests can substitute a mock
 * here instead of needing a reachable Temporal server; {@link WorkflowClient}'s own stub/proxy
 * machinery isn't something Mockito can stand in for directly.
 */
@Component
public class PaymentWorkflowStarter {

  private static final String PAYMENT_TASK_QUEUE = "PAYMENT_TASK_QUEUE";

  private final WorkflowClient workflowClient;

  public PaymentWorkflowStarter(WorkflowClient workflowClient) {
    this.workflowClient = workflowClient;
  }

  public void start(PaymentWorkflowInput input) {
    PaymentWorkflow workflow =
        workflowClient.newWorkflowStub(
            PaymentWorkflow.class,
            WorkflowOptions.newBuilder()
                .setWorkflowId(input.paymentId().toString())
                .setTaskQueue(PAYMENT_TASK_QUEUE)
                .build());
    WorkflowClient.start(workflow::execute, input);
  }
}
