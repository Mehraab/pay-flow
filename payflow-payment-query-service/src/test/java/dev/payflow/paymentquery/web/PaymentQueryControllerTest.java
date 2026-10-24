package dev.payflow.paymentquery.web;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.payflow.paymentquery.view.PaymentTimelineEvent;
import dev.payflow.paymentquery.view.PaymentView;
import dev.payflow.paymentquery.view.PaymentViewStore;
import java.time.Instant;
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

    mockMvc
        .perform(get("/payments/{id}", paymentId))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("Payment " + paymentId + " not found"))
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.path").value("/payments/" + paymentId));
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

  @Test
  void getSummaryReturnsWhatTheStoreReports() throws Exception {
    PaymentSummaryResponse summary =
        new PaymentSummaryResponse(100_000, "USD", 12.5, 98.5, 0.35, List.of(new DailyVolume("2026-01-01", 50_000)));
    when(store.getSummary(null, 30)).thenReturn(summary);

    mockMvc
        .perform(get("/payments/summary"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.netVolumeMinor").value(100_000))
        .andExpect(jsonPath("$.currency").value("USD"))
        .andExpect(jsonPath("$.successRatePct").value(98.5))
        .andExpect(jsonPath("$.dailyVolume[0].date").value("2026-01-01"));
  }

  @Test
  void getSummaryParsesTheMerchantIdHeaderAndDaysParam() throws Exception {
    UUID merchantId = UUID.randomUUID();
    when(store.getSummary(merchantId, 7))
        .thenReturn(new PaymentSummaryResponse(0, "USD", 0.0, 100.0, 0.35, List.of()));

    mockMvc
        .perform(get("/payments/summary").header("X-Merchant-Id", merchantId.toString()).param("days", "7"))
        .andExpect(status().isOk());

    verify(store).getSummary(merchantId, 7);
  }

  @Test
  void getTimelineReturnsWhatTheStoreReports() throws Exception {
    UUID paymentId = UUID.randomUUID();
    Instant timestamp = Instant.parse("2026-01-01T00:00:00Z");
    when(store.findTimeline(paymentId))
        .thenReturn(
            Optional.of(
                List.of(new PaymentTimelineEvent("PAYMENT_CREATED", "CREATED", timestamp, "Payment initialized for Elena Marsh"))));

    mockMvc
        .perform(get("/payments/{id}/events", paymentId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].eventType").value("PAYMENT_CREATED"))
        .andExpect(jsonPath("$[0].status").value("CREATED"));
  }

  @Test
  void getTimelineReturns404WhenThePaymentIsUnknown() throws Exception {
    UUID paymentId = UUID.randomUUID();
    when(store.findTimeline(paymentId)).thenReturn(Optional.empty());

    mockMvc
        .perform(get("/payments/{id}/events", paymentId))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("Timeline for payment " + paymentId + " not found"))
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.path").value("/payments/" + paymentId + "/events"));
  }
}
