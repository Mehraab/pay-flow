package dev.payflow.ledger.web;

import dev.payflow.ledger.payout.MerchantPayableAccountNotFoundException;
import dev.payflow.ledger.payout.NoPayoutBalanceAvailableException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * BE-11: maps known, client-triggerable domain failures to a proper status and message body —
 * deliberately narrow, not a blanket {@code IllegalStateException}/{@code RuntimeException}
 * handler. {@code server.error.include-message} stays at its default ({@code never}): flipping
 * it globally would leak internals for every unhandled exception service-wide, not just these
 * two. A plain {@code IllegalStateException} that isn't one of the two types below — the {@code
 * X-Force-Ledger-Failure} simulated fault in {@link LedgerController#postCapture}, or the
 * internal consistency check in {@code LedgerPostingService#resolveMerchantId} — is intentionally
 * left unhandled here and still surfaces as an opaque 500, because those represent a genuine
 * server-side fault or a data-integrity invariant violation, not a predictable business condition
 * a caller can act on.
 */
@RestControllerAdvice
public class LedgerExceptionHandler {

  @ExceptionHandler(MerchantPayableAccountNotFoundException.class)
  public ResponseEntity<Map<String, String>> handleAccountNotFound(MerchantPayableAccountNotFoundException e) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
  }

  @ExceptionHandler(NoPayoutBalanceAvailableException.class)
  public ResponseEntity<Map<String, String>> handleNoBalanceAvailable(NoPayoutBalanceAvailableException e) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
  }
}
