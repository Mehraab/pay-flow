package dev.payflow.orchestrator.saga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.temporal.client.WorkflowClient;
import org.junit.jupiter.api.Test;

class SagaEventsServiceTest {

  @Test
  void listSagaEventsReturnsAnEmptyListWhenTheVisibilityApiIsUnreachable() {
    WorkflowClient workflowClient = mock(WorkflowClient.class);
    when(workflowClient.listExecutions("WorkflowType = 'PaymentWorkflow'"))
        .thenThrow(new RuntimeException("Temporal server unreachable"));
    SagaEventsService service = new SagaEventsService(workflowClient);

    assertThat(service.listSagaEvents()).isEmpty();
  }
}
