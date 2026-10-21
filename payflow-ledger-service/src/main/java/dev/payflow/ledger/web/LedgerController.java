package dev.payflow.ledger.web;

import dev.payflow.common.domain.Currency;
import dev.payflow.common.domain.Money;
import dev.payflow.ledger.account.LedgerAccountService;
import dev.payflow.ledger.posting.LedgerPostingService;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
  public PostCaptureResponse postCapture(@RequestBody PostCaptureRequest request) {
    Money amount = Money.of(request.amountMinor(), Currency.valueOf(request.currency()));
    postingService.postPaymentCapture(request.paymentId(), request.merchantId(), amount);
    return new PostCaptureResponse("POSTED");
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
}
