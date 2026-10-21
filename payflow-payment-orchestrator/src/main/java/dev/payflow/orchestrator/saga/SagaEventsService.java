package dev.payflow.orchestrator.saga;

import io.temporal.api.enums.v1.WorkflowExecutionStatus;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowExecutionMetadata;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Reads PaymentWorkflow's saga history straight from Temporal's own Visibility API (BR-14) —
 * Temporal's execution history already is the durable saga audit log, so nothing is duplicated
 * into this service's own storage.
 */
@Service
public class SagaEventsService {

  private static final Logger log = LoggerFactory.getLogger(SagaEventsService.class);
  private static final String QUERY = "WorkflowType = 'PaymentWorkflow'";

  private final WorkflowClient workflowClient;

  public SagaEventsService(WorkflowClient workflowClient) {
    this.workflowClient = workflowClient;
  }

  /**
   * Returns an empty list — rather than propagating a connection failure — whenever the
   * Temporal server/Visibility API isn't reachable, the same defensive stance already taken by
   * {@code TemporalWorkerLifecycle} for the worker connection. This keeps local/unit-test
   * environments (no live Temporal server) able to exercise this endpoint cleanly.
   */
  public List<SagaEventResponse> listSagaEvents() {
    try {
      return workflowClient.listExecutions(QUERY).map(this::toResponse).toList();
    } catch (RuntimeException e) {
      log.warn("Could not reach the Temporal Visibility API — is the Temporal server running? {}", e.getMessage());
      return List.of();
    }
  }

  private SagaEventResponse toResponse(WorkflowExecutionMetadata metadata) {
    return new SagaEventResponse(
        metadata.getExecution().getWorkflowId(),
        mapStatus(metadata.getStatus()),
        metadata.getStartTime(),
        metadata.getCloseTime());
  }

  private String mapStatus(WorkflowExecutionStatus status) {
    return switch (status) {
      case WORKFLOW_EXECUTION_STATUS_COMPLETED -> "COMPLETED";
      case WORKFLOW_EXECUTION_STATUS_FAILED -> "FAILED";
      case WORKFLOW_EXECUTION_STATUS_TERMINATED -> "TERMINATED";
      case WORKFLOW_EXECUTION_STATUS_TIMED_OUT -> "TIMED_OUT";
      case WORKFLOW_EXECUTION_STATUS_RUNNING -> "RUNNING";
      case WORKFLOW_EXECUTION_STATUS_CANCELED -> "CANCELED";
      case WORKFLOW_EXECUTION_STATUS_CONTINUED_AS_NEW -> "CONTINUED_AS_NEW";
      default -> "UNKNOWN";
    };
  }
}
