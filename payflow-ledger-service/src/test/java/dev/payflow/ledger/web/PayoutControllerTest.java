package dev.payflow.ledger.web;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.payflow.ledger.payout.Payout;
import dev.payflow.ledger.payout.PayoutStatus;
import dev.payflow.ledger.posting.LedgerPostingService;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@WebMvcTest(controllers = PayoutController.class)
class PayoutControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @MockitoBean private LedgerPostingService postingService;

  @Test
  void createPayoutReturns201WithThePayoutDetails() throws Exception {
    UUID merchantId = UUID.randomUUID();
    Payout payout =
        new Payout(UUID.randomUUID(), merchantId, 20_779, "USD", PayoutStatus.IN_TRANSIT, Instant.now(), null);
    when(postingService.createPayout(merchantId)).thenReturn(payout);

    mockMvc
        .perform(
            post("/payouts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreatePayoutRequest(merchantId))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(payout.id().toString()))
        .andExpect(jsonPath("$.merchantId").value(merchantId.toString()))
        .andExpect(jsonPath("$.amountMinor").value(20_779))
        .andExpect(jsonPath("$.currency").value("USD"))
        .andExpect(jsonPath("$.status").value("IN_TRANSIT"));

    verify(postingService).createPayout(eq(merchantId));
  }

  @Test
  void getPayoutReturnsTheStoredPayout() throws Exception {
    UUID payoutId = UUID.randomUUID();
    UUID merchantId = UUID.randomUUID();
    Payout payout =
        new Payout(payoutId, merchantId, 20_779, "USD", PayoutStatus.IN_TRANSIT, Instant.now(), null);
    when(postingService.findPayoutById(payoutId)).thenReturn(Optional.of(payout));

    mockMvc
        .perform(get("/payouts/{id}", payoutId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(payoutId.toString()))
        .andExpect(jsonPath("$.status").value("IN_TRANSIT"));
  }

  @Test
  void getPayoutReturnsNotFoundWhenThePayoutDoesNotExist() throws Exception {
    UUID payoutId = UUID.randomUUID();
    when(postingService.findPayoutById(payoutId)).thenReturn(Optional.empty());

    mockMvc.perform(get("/payouts/{id}", payoutId)).andExpect(status().isNotFound());
  }
}
