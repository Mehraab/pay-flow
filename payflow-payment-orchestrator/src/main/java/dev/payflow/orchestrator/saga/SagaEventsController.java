package dev.payflow.orchestrator.saga;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Operator-only saga visibility (BR-14) — proxied by the gateway at {@code /operator/saga-events}. */
@RestController
public class SagaEventsController {

  private final SagaEventsService sagaEventsService;

  public SagaEventsController(SagaEventsService sagaEventsService) {
    this.sagaEventsService = sagaEventsService;
  }

  @GetMapping("/operator/saga-events")
  public List<SagaEventResponse> sagaEvents() {
    return sagaEventsService.listSagaEvents();
  }
}
