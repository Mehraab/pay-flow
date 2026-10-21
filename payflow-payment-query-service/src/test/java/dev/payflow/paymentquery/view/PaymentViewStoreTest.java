package dev.payflow.paymentquery.view;

import static org.assertj.core.api.Assertions.assertThat;

import dev.payflow.paymentquery.web.DailyVolume;
import dev.payflow.paymentquery.web.PageResponse;
import dev.payflow.paymentquery.web.PaymentSummaryResponse;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentViewStoreTest {

  private final PaymentViewStore store = new PaymentViewStore();

  private PaymentView viewOf(UUID paymentId, UUID merchantId, String status, String customer, Instant createdAt) {
    return viewOf(paymentId, merchantId, status, customer, createdAt, 21_400);
  }

  private PaymentView viewOf(
      UUID paymentId, UUID merchantId, String status, String customer, Instant createdAt, long amountMinor) {
    return new PaymentView(
        paymentId,
        merchantId,
        amountMinor,
        "USD",
        status,
        null,
        null,
        createdAt,
        customer,
        new PaymentMethodView("Visa", "4242"),
        null,
        null);
  }

  private PaymentView viewWithOutcome(
      UUID paymentId, String status, String providerRef, String providerDisplayName, String declineReason) {
    return new PaymentView(
        paymentId,
        UUID.randomUUID(),
        21_400,
        "USD",
        status,
        providerRef,
        declineReason,
        Instant.now(),
        "Elena Marsh",
        new PaymentMethodView("Visa", "4242"),
        null,
        providerDisplayName);
  }

  @Test
  void findPaymentsFiltersByMerchantId() {
    UUID targetMerchant = UUID.randomUUID();
    UUID otherMerchant = UUID.randomUUID();
    store.save(viewOf(UUID.randomUUID(), targetMerchant, "CREATED", "Elena Marsh", Instant.now()));
    store.save(viewOf(UUID.randomUUID(), otherMerchant, "CREATED", "Someone Else", Instant.now()));

    PageResponse<PaymentView> page = store.findPayments(targetMerchant, null, null, 0, 10);

    assertThat(page.content()).extracting(PaymentView::merchantId).containsOnly(targetMerchant);
    assertThat(page.totalElements()).isEqualTo(1);
  }

  @Test
  void findPaymentsFiltersByStatusCaseInsensitively() {
    store.save(viewOf(UUID.randomUUID(), UUID.randomUUID(), "CAPTURED", "A", Instant.now()));
    store.save(viewOf(UUID.randomUUID(), UUID.randomUUID(), "FAILED", "B", Instant.now()));

    PageResponse<PaymentView> page = store.findPayments(null, "captured", null, 0, 10);

    assertThat(page.content()).extracting(PaymentView::status).containsOnly("CAPTURED");
  }

  @Test
  void findPaymentsSearchesByCustomerNameCaseInsensitively() {
    store.save(viewOf(UUID.randomUUID(), UUID.randomUUID(), "CREATED", "Elena Marsh", Instant.now()));
    store.save(viewOf(UUID.randomUUID(), UUID.randomUUID(), "CREATED", "John Doe", Instant.now()));

    PageResponse<PaymentView> page = store.findPayments(null, null, "elena", 0, 10);

    assertThat(page.content()).extracting(PaymentView::customer).containsExactly("Elena Marsh");
  }

  @Test
  void findPaymentsSearchesByPaymentIdSubstring() {
    UUID paymentId = UUID.randomUUID();
    store.save(viewOf(paymentId, UUID.randomUUID(), "CREATED", "A", Instant.now()));
    store.save(viewOf(UUID.randomUUID(), UUID.randomUUID(), "CREATED", "B", Instant.now()));

    PageResponse<PaymentView> page =
        store.findPayments(null, null, paymentId.toString().substring(0, 8), 0, 10);

    assertThat(page.content()).extracting(PaymentView::paymentId).containsExactly(paymentId);
  }

  @Test
  void findPaymentsSortsByCreatedAtDescending() {
    UUID oldest = UUID.randomUUID();
    UUID newest = UUID.randomUUID();
    Instant now = Instant.now();
    store.save(viewOf(oldest, UUID.randomUUID(), "CREATED", "A", now.minusSeconds(60)));
    store.save(viewOf(newest, UUID.randomUUID(), "CREATED", "B", now));

    PageResponse<PaymentView> page = store.findPayments(null, null, null, 0, 10);

    assertThat(page.content()).extracting(PaymentView::paymentId).containsExactly(newest, oldest);
  }

  @Test
  void findPaymentsPaginatesAcrossMultiplePages() {
    Instant now = Instant.now();
    for (int i = 0; i < 5; i++) {
      store.save(viewOf(UUID.randomUUID(), UUID.randomUUID(), "CREATED", "Customer " + i, now.plusSeconds(i)));
    }

    PageResponse<PaymentView> firstPage = store.findPayments(null, null, null, 0, 2);
    PageResponse<PaymentView> secondPage = store.findPayments(null, null, null, 1, 2);
    PageResponse<PaymentView> thirdPage = store.findPayments(null, null, null, 2, 2);

    assertThat(firstPage.content()).hasSize(2);
    assertThat(secondPage.content()).hasSize(2);
    assertThat(thirdPage.content()).hasSize(1);
    assertThat(firstPage.totalElements()).isEqualTo(5);
    assertThat(firstPage.totalPages()).isEqualTo(3);
  }

  @Test
  void findPaymentsReturnsAnEmptyPageWhenThePageIsPastTheEnd() {
    store.save(viewOf(UUID.randomUUID(), UUID.randomUUID(), "CREATED", "A", Instant.now()));

    PageResponse<PaymentView> page = store.findPayments(null, null, null, 5, 10);

    assertThat(page.content()).isEmpty();
    assertThat(page.totalElements()).isEqualTo(1);
  }

  @Test
  void getSummaryNetsCapturedVolumeAgainstRefundedVolume() {
    Instant now = Instant.now();
    store.save(viewOf(UUID.randomUUID(), UUID.randomUUID(), "CAPTURED", "A", now, 20_000));
    store.save(viewOf(UUID.randomUUID(), UUID.randomUUID(), "REFUNDED", "B", now, 5_000));

    PaymentSummaryResponse summary = store.getSummary(null, 30);

    assertThat(summary.netVolumeMinor()).isEqualTo(15_000);
    assertThat(summary.currency()).isEqualTo("USD");
  }

  @Test
  void getSummaryScopesToTheGivenMerchantId() {
    UUID targetMerchant = UUID.randomUUID();
    Instant now = Instant.now();
    store.save(viewOf(UUID.randomUUID(), targetMerchant, "CAPTURED", "A", now, 10_000));
    store.save(viewOf(UUID.randomUUID(), UUID.randomUUID(), "CAPTURED", "B", now, 90_000));

    PaymentSummaryResponse summary = store.getSummary(targetMerchant, 30);

    assertThat(summary.netVolumeMinor()).isEqualTo(10_000);
  }

  @Test
  void getSummaryExcludesPaymentsOutsideTheWindow() {
    store.save(viewOf(UUID.randomUUID(), UUID.randomUUID(), "CAPTURED", "A", Instant.now().minus(60, ChronoUnit.DAYS), 10_000));

    PaymentSummaryResponse summary = store.getSummary(null, 30);

    assertThat(summary.netVolumeMinor()).isZero();
    assertThat(summary.volumeDeltaPct()).isZero();
  }

  @Test
  void getSummaryComputesSuccessRateFromCapturedAndFailedCounts() {
    Instant now = Instant.now();
    store.save(viewOf(UUID.randomUUID(), UUID.randomUUID(), "CAPTURED", "A", now));
    store.save(viewOf(UUID.randomUUID(), UUID.randomUUID(), "CAPTURED", "B", now));
    store.save(viewOf(UUID.randomUUID(), UUID.randomUUID(), "CAPTURED", "C", now));
    store.save(viewOf(UUID.randomUUID(), UUID.randomUUID(), "FAILED", "D", now));

    PaymentSummaryResponse summary = store.getSummary(null, 30);

    assertThat(summary.successRatePct()).isEqualTo(75.0);
    assertThat(summary.volumeDeltaPct()).isEqualTo(12.5);
  }

  @Test
  void getSummaryReturnsFullSuccessRateWhenThereAreNoAttempts() {
    PaymentSummaryResponse summary = store.getSummary(null, 30);

    assertThat(summary.successRatePct()).isEqualTo(100.0);
    assertThat(summary.volumeDeltaPct()).isZero();
  }

  @Test
  void getSummaryBucketsDailyVolumeByCapturedAmountSortedAscending() {
    Instant today = Instant.now();
    Instant yesterday = today.minus(1, ChronoUnit.DAYS);
    store.save(viewOf(UUID.randomUUID(), UUID.randomUUID(), "CAPTURED", "A", yesterday, 1_000));
    store.save(viewOf(UUID.randomUUID(), UUID.randomUUID(), "CAPTURED", "B", today, 2_000));
    // A FAILED payment must not contribute to the daily volume chart.
    store.save(viewOf(UUID.randomUUID(), UUID.randomUUID(), "FAILED", "C", today, 999_999));

    PaymentSummaryResponse summary = store.getSummary(null, 2);

    assertThat(summary.dailyVolume()).hasSize(2);
    assertThat(summary.dailyVolume()).isSortedAccordingTo((a, b) -> a.date().compareTo(b.date()));
    long total = summary.dailyVolume().stream().mapToLong(DailyVolume::amountMinor).sum();
    assertThat(total).isEqualTo(3_000);
  }

  @Test
  void findTimelineReturnsRecordedEventsWhenTheyExist() {
    UUID paymentId = UUID.randomUUID();
    store.save(viewOf(paymentId, UUID.randomUUID(), "CREATED", "Elena Marsh", Instant.now()));
    PaymentTimelineEvent event = new PaymentTimelineEvent("PAYMENT_CREATED", "CREATED", Instant.now(), "Payment initialized for Elena Marsh");
    store.recordTimelineEvent(paymentId, event);

    Optional<List<PaymentTimelineEvent>> timeline = store.findTimeline(paymentId);

    assertThat(timeline).isPresent();
    assertThat(timeline.get()).containsExactly(event);
  }

  @Test
  void findTimelineReturnsEmptyWhenThePaymentIsUnknown() {
    assertThat(store.findTimeline(UUID.randomUUID())).isEmpty();
  }

  @Test
  void findTimelineSynthesizesACapturedTimelineWhenNoneWasRecorded() {
    UUID paymentId = UUID.randomUUID();
    store.save(viewWithOutcome(paymentId, "CAPTURED", "mock-ch-xyz", "Mock Simulator", null));

    List<PaymentTimelineEvent> timeline = store.findTimeline(paymentId).orElseThrow();

    assertThat(timeline).extracting(PaymentTimelineEvent::eventType)
        .containsExactly("PAYMENT_CREATED", "PAYMENT_CAPTURED", "LEDGER_POSTED", "WEBHOOK_DELIVERED");
    assertThat(timeline.get(1).description()).contains("mock-ch-xyz");
  }

  @Test
  void findTimelineSynthesizesAFailedTimelineWhenNoneWasRecorded() {
    UUID paymentId = UUID.randomUUID();
    store.save(viewWithOutcome(paymentId, "FAILED", null, null, "Insufficient funds"));

    List<PaymentTimelineEvent> timeline = store.findTimeline(paymentId).orElseThrow();

    assertThat(timeline).extracting(PaymentTimelineEvent::eventType).containsExactly("PAYMENT_CREATED", "PAYMENT_FAILED");
    assertThat(timeline.get(1).description()).contains("Insufficient funds");
  }

  @Test
  void findTimelineSynthesizesARefundedTimelineWhenNoneWasRecorded() {
    UUID paymentId = UUID.randomUUID();
    store.save(viewWithOutcome(paymentId, "REFUNDED", "mock-ch-xyz", "Mock Simulator", null));

    List<PaymentTimelineEvent> timeline = store.findTimeline(paymentId).orElseThrow();

    assertThat(timeline)
        .extracting(PaymentTimelineEvent::eventType)
        .containsExactly("PAYMENT_CREATED", "PAYMENT_CAPTURED", "LEDGER_POSTED", "WEBHOOK_DELIVERED", "PAYMENT_REFUNDED");
  }
}
