package dev.payflow.ledger.payout;

import dev.payflow.ledger.posting.LedgerPostingService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Simulates the real-world latency of a wire/ACH payout settling with the bank
 * (ARCHITECTURE_AND_PLAN.md §2.3.3, BR-15 Phase B) — a payout sits {@code IN_TRANSIT} for at
 * least {@link #SETTLEMENT_DELAY} before this polls it into {@code SETTLED}, rather than
 * settling instantly.
 */
@Component
public class SettlementSimulator {

  private static final Duration SETTLEMENT_DELAY = Duration.ofSeconds(2);

  private final PayoutRepository payoutRepository;
  private final LedgerPostingService postingService;

  public SettlementSimulator(PayoutRepository payoutRepository, LedgerPostingService postingService) {
    this.payoutRepository = payoutRepository;
    this.postingService = postingService;
  }

  @Scheduled(fixedDelay = 1000)
  public void settleEligiblePayouts() {
    List<Payout> pending = payoutRepository.findPendingSettlement();
    Instant now = Instant.now();
    for (Payout payout : pending) {
      if (!Duration.between(payout.createdAt(), now).minus(SETTLEMENT_DELAY).isNegative()) {
        postingService.settlePayout(payout.id());
      }
    }
  }
}
