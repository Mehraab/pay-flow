package dev.payflow.ledger.web;

import dev.payflow.common.domain.Currency;
import dev.payflow.common.domain.Money;
import dev.payflow.ledger.account.LedgerAccountService;
import dev.payflow.ledger.posting.LedgerEntry;
import dev.payflow.ledger.posting.LedgerPostingService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LedgerController {

  private final LedgerPostingService postingService;
  private final LedgerAccountService accountService;

  public LedgerController(LedgerPostingService postingService, LedgerAccountService accountService) {
    this.postingService = postingService;
    this.accountService = accountService;
  }

  @PostMapping("/internal/ledger/post-capture")
  public PostCaptureResponse postCapture(
      @RequestHeader(value = "X-Force-Ledger-Failure", required = false) Boolean forceFailure,
      @RequestBody PostCaptureRequest request) {
    if (Boolean.TRUE.equals(forceFailure)) {
      throw new IllegalStateException("Simulated ledger failure for payment " + request.paymentId());
    }
    Money amount = Money.of(request.amountMinor(), Currency.valueOf(request.currency()));
    postingService.postPaymentCapture(request.paymentId(), request.merchantId(), amount);
    return new PostCaptureResponse("POSTED");
  }

  @GetMapping("/ledger")
  public List<LedgerEntryResponse> entriesForTransaction(@RequestParam("transaction") String transaction) {
    return postingService.findEntriesForTransaction(transaction).stream().map(this::toResponse).toList();
  }

  private LedgerEntryResponse toResponse(LedgerEntry entry) {
    return new LedgerEntryResponse(
        entry.id(),
        entry.transactionRef(),
        entry.debitAccountId(),
        entry.creditAccountId(),
        entry.amountMinor(),
        entry.postedAt());
  }

  @GetMapping("/accounts/{accountName}/balance")
  public ResponseEntity<AccountBalanceResponse> platformAccountBalance(@PathVariable String accountName) {
    return accountService
        .findPlatformAccount(accountName)
        .or(() -> accountService.findUniqueMerchantAccountByNameOrSlug(accountName))
        .map(acc -> ResponseEntity.ok(new AccountBalanceResponse(acc.accountName(), acc.balanceMinor())))
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @GetMapping("/accounts/{merchantId}/{accountName}/balance")
  public ResponseEntity<AccountBalanceResponse> merchantAccountBalance(
      @PathVariable UUID merchantId, @PathVariable String accountName) {
    return accountService
        .findMerchantAccount(merchantId, accountName)
        .map(acc -> ResponseEntity.ok(new AccountBalanceResponse(acc.accountName(), acc.balanceMinor())))
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @GetMapping("/operator/ledger/integrity")
  public LedgerIntegrityResponse ledgerIntegrity() {
    long totalDebitsMinor = postingService.sumTotalDebits();
    long totalCreditsMinor = postingService.sumTotalCredits();
    return new LedgerIntegrityResponse(totalDebitsMinor, totalCreditsMinor, totalDebitsMinor == totalCreditsMinor);
  }
}
