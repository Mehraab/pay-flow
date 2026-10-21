package dev.payflow.providergateway.routing;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@WebMvcTest(controllers = ProviderRoutingController.class)
class ProviderRoutingControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @MockitoBean private ProviderRoutingService routingService;

  @Test
  void listProvidersReturnsTheConfiguredProviders() throws Exception {
    when(routingService.listProviders())
        .thenReturn(
            List.of(
                new ProviderConfig("mock-primary", "Mock Primary Processor", 1, true),
                new ProviderConfig("mock-secondary", "Mock Backup Processor", 2, true)));

    mockMvc
        .perform(get("/operator/providers"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].id").value("mock-primary"));
  }

  @Test
  void updateRoutingReturnsTheUpdatedProvider() throws Exception {
    when(routingService.updateRouting(eq("mock-primary"), any()))
        .thenReturn(new ProviderConfig("mock-primary", "Mock Primary Processor", 5, false));

    mockMvc
        .perform(
            patch("/operator/providers/{id}/routing", "mock-primary")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new UpdateRoutingRequest(5, false))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.priority").value(5))
        .andExpect(jsonPath("$.active").value(false));
  }

  @Test
  void updateRoutingReturnsNotFoundForAnUnknownProvider() throws Exception {
    when(routingService.updateRouting(eq("unknown"), any()))
        .thenThrow(new NoSuchElementException("No provider configured with id 'unknown'"));

    mockMvc
        .perform(
            patch("/operator/providers/{id}/routing", "unknown")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new UpdateRoutingRequest(1, true))))
        .andExpect(status().isNotFound());
  }
}
