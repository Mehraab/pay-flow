package dev.payflow.orchestrator.saga;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = SagaEventsController.class)
class SagaEventsControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private SagaEventsService sagaEventsService;

  @Test
  void sagaEventsReturnsWhatTheServiceReports() throws Exception {
    Instant start = Instant.parse("2026-01-01T00:00:00Z");
    Instant close = Instant.parse("2026-01-01T00:00:05Z");
    when(sagaEventsService.listSagaEvents())
        .thenReturn(List.of(new SagaEventResponse("payment-abc", "COMPLETED", start, close)));

    mockMvc
        .perform(get("/operator/saga-events"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].workflowId").value("payment-abc"))
        .andExpect(jsonPath("$[0].status").value("COMPLETED"));
  }

  @Test
  void sagaEventsReturnsAnEmptyListWhenNoWorkflowsHaveRun() throws Exception {
    when(sagaEventsService.listSagaEvents()).thenReturn(List.of());

    mockMvc
        .perform(get("/operator/saga-events"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$").isEmpty());
  }
}
