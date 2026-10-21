package dev.payflow.ledger.payout;

import dev.payflow.ledger.posting.LedgerPostingService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Default cron fires once daily at 02:00 — overridable via {@code payflow.ledger.eod-sweep-cron}. */
@Component
public class EodSweepScheduler {

  private final LedgerPostingService postingService;

  public EodSweepScheduler(LedgerPostingService postingService) {
    this.postingService = postingService;
  }

  @Scheduled(cron = "${payflow.ledger.eod-sweep-cron:0 0 2 * * *}")
  public void runEodSweep() {
    postingService.sweepAllEligibleMerchants();
  }
}
