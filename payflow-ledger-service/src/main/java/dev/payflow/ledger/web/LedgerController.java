package dev.payflow.ledger.web;

import dev.payflow.common.domain.Currency;
import dev.payflow.common.domain.Money;
import dev.payflow.common.dto.ErrorResponse;
import dev.payflow.ledger.account.LedgerAccountService;
import dev.payflow.ledger.posting.LedgerEntry;
import dev.payflow.ledger.posting.LedgerPostingService;
import java.time.Instant;
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
      // BE-11: deliberately a plain, uncaught IllegalStateException (-> 500), not one of
      // LedgerExceptionHandler's mapped types — this simulates a genuine ledger-side outage for
      // the saga-compensation tests in payflow-payment-orchestrator, which needs an opaque
      // server fault here, not a clean 4xx.
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
  public ResponseEntity<?> platformAccountBalance(@PathVariable String accountName) {
    return accountService
        .findPlatformAccount(accountName)
        .or(() -> accountService.findUniqueMerchantAccountByNameOrSlug(accountName))
        .<ResponseEntity<?>>map(acc -> ResponseEntity.ok(new AccountBalanceResponse(acc.accountName(), acc.balanceMinor())))
        .orElseGet(
            () ->
                ResponseEntity.status(404)
                    .body(ErrorResponse.of("Account '" + accountName + "' not found", 404)));
  }

  @GetMapping("/accounts/{merchantId}/{accountName}/balance")
  public ResponseEntity<?> merchantAccountBalance(@PathVariable UUID merchantId, @PathVariable String accountName) {
    return accountService
        .findMerchantAccount(merchantId, accountName)
        .<ResponseEntity<?>>map(acc -> ResponseEntity.ok(new AccountBalanceResponse(acc.accountName(), acc.balanceMinor())))
        .orElseGet(
            () ->
                ResponseEntity.status(404)
                    .body(
                        ErrorResponse.of("Account '" + accountName + "' not found for merchant " + merchantId, 404)));
  }

  @GetMapping("/operator/ledger/integrity")
  public LedgerIntegrityResponse ledgerIntegrity() {
    long totalDebitsMinor = postingService.sumTotalDebits();
    long totalCreditsMinor = postingService.sumTotalCredits();
    return new LedgerIntegrityResponse(totalDebitsMinor, totalCreditsMinor, totalDebitsMinor == totalCreditsMinor);
  }

  @GetMapping("/operator/ledger/entries")
  public PageResponse<LedgerEntryResponse> listLedgerEntries(
      @RequestParam(value = "since", required = false) Instant since,
      @RequestParam(value = "page", defaultValue = "0") int page,
      @RequestParam(value = "size", defaultValue = "20") int size) {
    List<LedgerEntryResponse> content =
        postingService.findEntriesPaged(since, page, size).stream().map(this::toResponse).toList();
    long totalElements = postingService.countEntriesSince(since);
    int totalPages = size > 0 ? (int) Math.ceil((double) totalElements / size) : 0;
    return new PageResponse<>(content, page, size, totalElements, totalPages);
  }

  @GetMapping("/ledger/merchants/{merchantId}/entries")
  public ResponseEntity<?> merchantLedgerEntries(
      @PathVariable UUID merchantId,
      @RequestHeader(value = "X-Merchant-Id", required = false) String merchantIdHeader,
      @RequestParam(value = "page", defaultValue = "0") int page,
      @RequestParam(value = "size", defaultValue = "20") int size) {
    if (merchantIdHeader != null && !merchantId.toString().equalsIgnoreCase(merchantIdHeader)) {
      return ResponseEntity.status(403)
          .body(ErrorResponse.of("X-Merchant-Id header does not match the requested merchantId", 403));
    }
    List<LedgerEntryResponse> content =
        postingService.findMerchantEntriesPaged(merchantId, page, size).stream().map(this::toResponse).toList();
    long totalElements = postingService.countMerchantEntries(merchantId);
    int totalPages = size > 0 ? (int) Math.ceil((double) totalElements / size) : 0;
    return ResponseEntity.ok(new PageResponse<>(content, page, size, totalElements, totalPages));
  }
}
