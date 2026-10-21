package dev.payflow.paymentquery.web;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.payflow.paymentquery.view.PaymentView;
import dev.payflow.paymentquery.view.PaymentViewStore;
import java.util.List;
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

  @Test
  void listPaymentsReturnsThePageTheStoreReports() throws Exception {
    UUID paymentId = UUID.randomUUID();
    UUID merchantId = UUID.randomUUID();
    PaymentView view = new PaymentView(paymentId, merchantId, 21_400, "USD", "CAPTURED", "mock-ch-abc123", null);
    when(store.findPayments(isNull(), isNull(), isNull(), eq(0), eq(10)))
        .thenReturn(new PageResponse<>(List.of(view), 0, 10, 1, 1));

    mockMvc
        .perform(get("/payments"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].paymentId").value(paymentId.toString()))
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.size").value(10))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.totalPages").value(1));
  }

  @Test
  void listPaymentsParsesTheMerchantIdHeaderAndScopesTheQuery() throws Exception {
    UUID merchantId = UUID.randomUUID();
    when(store.findPayments(eq(merchantId), isNull(), isNull(), eq(0), eq(10)))
        .thenReturn(new PageResponse<>(List.of(), 0, 10, 0, 0));

    mockMvc
        .perform(get("/payments").header("X-Merchant-Id", merchantId.toString()))
        .andExpect(status().isOk());

    verify(store).findPayments(merchantId, null, null, 0, 10);
  }

  @Test
  void listPaymentsForwardsStatusAndSearchQueryParams() throws Exception {
    when(store.findPayments(isNull(), eq("CAPTURED"), eq("Elena"), eq(2), eq(5)))
        .thenReturn(new PageResponse<>(List.of(), 2, 5, 0, 0));

    mockMvc
        .perform(get("/payments").param("status", "CAPTURED").param("q", "Elena").param("page", "2").param("size", "5"))
        .andExpect(status().isOk());

    verify(store).findPayments(null, "CAPTURED", "Elena", 2, 5);
  }
}
