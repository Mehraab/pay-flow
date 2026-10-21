package dev.payflow.ledger.consumer;

import static org.assertj.core.api.Assertions.assertThat;

import dev.payflow.common.domain.Currency;
import dev.payflow.common.domain.Money;
import dev.payflow.ledger.AbstractPostgresIntegrationTest;
import dev.payflow.ledger.account.LedgerAccount;
import dev.payflow.ledger.account.LedgerAccountService;
import dev.payflow.ledger.posting.LedgerPostingService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * No Kafka broker involved — exercises the listener method directly with the same (key,
 * event-type header, JSON payload) shape a real {@code @KafkaListener} invocation would deliver,
 * same pattern as payment-query-service's {@code PaymentEventConsumerTest}.
 */
@SpringBootTest
class PaymentRefundedConsumerTest extends AbstractPostgresIntegrationTest {

  @Autowired private PaymentRefundedConsumer consumer;
  @Autowired private LedgerPostingService postingService;
  @Autowired private LedgerAccountService accountService;
  @Autowired private NamedParameterJdbcTemplate jdbc;

  private String refundedPayloadFor(UUID eventId) {
    return "{\"metadata\":{\"eventId\":\"" + eventId + "\"}}";
  }

  @Test
  void aRefundedEventReversesACapturedPayment() {
    UUID paymentId = UUID.randomUUID();
    UUID merchantId = UUID.randomUUID();
    Money amount = Money.of(21_400, Currency.USD);
    postingService.postPaymentCapture(paymentId, merchantId, amount);

    UUID eventId = UUID.randomUUID();
    consumer.onPaymentEvent(paymentId.toString(), "payment.refunded", refundedPayloadFor(eventId));

    LedgerAccount merchantPayable =
        accountService.findMerchantAccount(merchantId, "Merchant Payable").orElseThrow();
    assertThat(merchantPayable.balanceMinor()).isZero();
  }

  @Test
  void duplicateRefundEventsWithTheSameEventIdAreProcessedOnlyOnce() {
    UUID paymentId = UUID.randomUUID();
    UUID merchantId = UUID.randomUUID();
    Money amount = Money.of(21_400, Currency.USD);
    postingService.postPaymentCapture(paymentId, merchantId, amount);

    UUID eventId = UUID.randomUUID();
    String payload = refundedPayloadFor(eventId);

    consumer.onPaymentEvent(paymentId.toString(), "payment.refunded", payload);
    consumer.onPaymentEvent(paymentId.toString(), "payment.refunded", payload); // exact replay

    LedgerAccount merchantPayable =
        accountService.findMerchantAccount(merchantId, "Merchant Payable").orElseThrow();
    // Zero, not negative — a second reversal would have pushed this to -20_779.
    assertThat(merchantPayable.balanceMinor()).isZero();

    Integer processedCount =
        jdbc.queryForObject(
            "SELECT count(*) FROM processed_events WHERE event_id = :eventId AND consumer_name = 'ledger-service'",
            new MapSqlParameterSource("eventId", eventId),
            Integer.class);
    assertThat(processedCount).isEqualTo(1);

    Integer entryCount =
        jdbc.queryForObject(
            "SELECT count(*) FROM ledger_entries WHERE transaction_ref = :ref",
            new MapSqlParameterSource("ref", paymentId.toString()),
            Integer.class);
    assertThat(entryCount).isEqualTo(4); // 2 capture legs + 2 reversal legs, not 6
  }

  @Test
  void aRefundedEventForAPaymentNeverCapturedAtTheLedgerPostsNothing() {
    UUID paymentId = UUID.randomUUID();

    consumer.onPaymentEvent(paymentId.toString(), "payment.refunded", refundedPayloadFor(UUID.randomUUID()));

    Integer entryCount =
        jdbc.queryForObject(
            "SELECT count(*) FROM ledger_entries WHERE transaction_ref = :ref",
            new MapSqlParameterSource("ref", paymentId.toString()),
            Integer.class);
    assertThat(entryCount).isZero();
  }

  @Test
  void nonRefundedEventTypesAreIgnored() {
    UUID paymentId = UUID.randomUUID();

    consumer.onPaymentEvent(paymentId.toString(), "payment.captured", "{\"providerRef\":\"mock-ch-abc123\"}");

    Integer entryCount =
        jdbc.queryForObject(
            "SELECT count(*) FROM ledger_entries WHERE transaction_ref = :ref",
            new MapSqlParameterSource("ref", paymentId.toString()),
            Integer.class);
    assertThat(entryCount).isZero();
  }
}
