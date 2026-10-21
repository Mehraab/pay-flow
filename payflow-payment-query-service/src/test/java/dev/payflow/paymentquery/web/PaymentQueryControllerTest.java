package dev.payflow.paymentquery.web;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.payflow.paymentquery.view.PaymentView;
import dev.payflow.paymentquery.view.PaymentViewStore;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = PaymentQueryController.class)
class PaymentQueryControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private PaymentViewStore store;

  @Test
  void returnsTheViewWhenThePaymentExists() throws Exception {
    UUID paymentId = UUID.randomUUID();
    UUID merchantId = UUID.randomUUID();
    PaymentView view =
        new PaymentView(paymentId, merchantId, 21_400, "USD", "CAPTURED", "mock-ch-abc123", null);
    when(store.findById(paymentId)).thenReturn(Optional.of(view));

    mockMvc
        .perform(get("/payments/{id}", paymentId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CAPTURED"))
        .andExpect(jsonPath("$.providerRef").value("mock-ch-abc123"))
        .andExpect(jsonPath("$.amountMinor").value(21_400));
  }

  @Test
  void returns404WhenThePaymentIsUnknown() throws Exception {
    UUID paymentId = UUID.randomUUID();
    when(store.findById(paymentId)).thenReturn(Optional.empty());

    mockMvc.perform(get("/payments/{id}", paymentId)).andExpect(status().isNotFound());
  }
}
