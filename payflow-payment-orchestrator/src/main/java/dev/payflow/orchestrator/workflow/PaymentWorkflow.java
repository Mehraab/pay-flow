package dev.payflow.orchestrator.workflow;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface PaymentWorkflow {

  @WorkflowMethod
  void execute(PaymentWorkflowInput input);
}
