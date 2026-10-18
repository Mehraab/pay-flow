package dev.payflow.providergateway;

import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.payflow.common.domain.Currency;
import dev.payflow.common.domain.Money;
import dev.payflow.common.dto.AuthorizePaymentRequest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@WebMvcTest(controllers = ProviderGatewayController.class)
@Import(MockProviderGateway.class)
class ProviderGatewayControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @Test
  void chargingAnApprovedTestCardReturnsApprovedWithAProviderReference() throws Exception {
    var request =
        new AuthorizePaymentRequest(
            UUID.randomUUID(), Money.of(21400, Currency.USD), "4242424242424242");

    mockMvc
        .perform(
            post("/internal/providers/charge")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("APPROVED"))
        .andExpect(jsonPath("$.providerRef", startsWith("mock-ch-")))
        .andExpect(jsonPath("$.declineReason").value(nullValue()));
  }

  @Test
  void chargingAnUnsupportedCardReturnsDeclined() throws Exception {
    var request =
        new AuthorizePaymentRequest(
            UUID.randomUUID(), Money.of(21400, Currency.USD), "4000000000000002");

    mockMvc
        .perform(
            post("/internal/providers/charge")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DECLINED"))
        .andExpect(jsonPath("$.providerRef").value(nullValue()))
        .andExpect(jsonPath("$.declineReason").value("Card not supported in Milestone 1"));
  }

  @Test
  void voidingAChargeReturnsOk() throws Exception {
    mockMvc
        .perform(
            post("/internal/providers/void")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"providerRef\":\"mock-ch-abc123\"}"))
        .andExpect(status().isOk());
  }
}
