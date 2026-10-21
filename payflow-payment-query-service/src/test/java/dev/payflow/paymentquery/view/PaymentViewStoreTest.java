package dev.payflow.paymentquery.view;

import static org.assertj.core.api.Assertions.assertThat;

import dev.payflow.paymentquery.web.PageResponse;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentViewStoreTest {

  private final PaymentViewStore store = new PaymentViewStore();

  private PaymentView viewOf(UUID paymentId, UUID merchantId, String status, String customer, Instant createdAt) {
    return new PaymentView(
        paymentId,
        merchantId,
        21_400,
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
}
