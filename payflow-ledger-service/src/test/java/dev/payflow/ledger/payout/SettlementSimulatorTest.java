package dev.payflow.ledger.payout;

import static org.assertj.core.api.Assertions.assertThat;

import dev.payflow.common.domain.Currency;
import dev.payflow.common.domain.Money;
import dev.payflow.ledger.AbstractPostgresIntegrationTest;
import dev.payflow.ledger.posting.LedgerPostingService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Exercises the scheduled sweep method directly rather than waiting on its real
 * {@code @Scheduled(fixedDelay = 1000)} trigger, and backdates {@code created_at} via raw SQL
 * rather than sleeping 2+ real seconds per test case — both keep this test fast and deterministic.
 */
@SpringBootTest
class SettlementSimulatorTest extends AbstractPostgresIntegrationTest {

  @Autowired private LedgerPostingService postingService;
  @Autowired private SettlementSimulator settlementSimulator;
  @Autowired private NamedParameterJdbcTemplate jdbc;

  private void backdateCreatedAt(UUID payoutId, int secondsAgo) {
    jdbc.update(
        "UPDATE payouts SET created_at = now() - (:seconds * interval '1 second') WHERE id = :id",
        new MapSqlParameterSource().addValue("seconds", secondsAgo).addValue("id", payoutId));
  }

  @Test
  void settlesAPayoutThatHasBeenInTransitLongEnough() {
    UUID merchantId = UUID.randomUUID();
    postingService.postPaymentCapture(UUID.randomUUID(), merchantId, Money.of(21_400, Currency.USD));
    Payout payout = postingService.createPayout(merchantId);
    backdateCreatedAt(payout.id(), 5);

    settlementSimulator.settleEligiblePayouts();

    assertThat(postingService.findPayoutById(payout.id()).orElseThrow().status())
        .isEqualTo(PayoutStatus.SETTLED);
  }

  @Test
  void leavesARecentlyCreatedPayoutInTransit() {
    UUID merchantId = UUID.randomUUID();
    postingService.postPaymentCapture(UUID.randomUUID(), merchantId, Money.of(21_400, Currency.USD));
    Payout payout = postingService.createPayout(merchantId); // created_at is "now" — too recent

    settlementSimulator.settleEligiblePayouts();

    assertThat(postingService.findPayoutById(payout.id()).orElseThrow().status())
        .isEqualTo(PayoutStatus.IN_TRANSIT);
  }
}
