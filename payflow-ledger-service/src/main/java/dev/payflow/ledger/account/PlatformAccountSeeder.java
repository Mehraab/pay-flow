package dev.payflow.ledger.account;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Ensures the platform-level chart of accounts exists before any posting happens. */
@Component
public class PlatformAccountSeeder implements ApplicationRunner {

  private final LedgerAccountService accountService;

  public PlatformAccountSeeder(LedgerAccountService accountService) {
    this.accountService = accountService;
  }

  @Override
  public void run(ApplicationArguments args) {
    accountService.ensurePlatformAccount("Platform Bank", AccountType.ASSET, NormalBalance.DEBIT);
    accountService.ensurePlatformAccount(
        "Platform Fee Revenue", AccountType.REVENUE, NormalBalance.CREDIT);
    accountService.ensurePlatformAccount(
        "Provider Settlement", AccountType.ASSET, NormalBalance.DEBIT);
  }
}
