package dev.payflow.ledger.web;

import dev.payflow.common.dto.ErrorResponse;
import dev.payflow.ledger.payout.MerchantPayableAccountNotFoundException;
import dev.payflow.ledger.payout.NoPayoutBalanceAvailableException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * BE-11/BE-12: maps known, client-triggerable domain failures to a proper status and a uniform
 * {@link ErrorResponse} body — deliberately narrow, not a blanket {@code
 * IllegalStateException}/{@code RuntimeException} handler. {@code server.error.include-message}
 * stays at its default ({@code never}): flipping it globally would leak internals for every
 * unhandled exception service-wide, not just these two. A plain {@code IllegalStateException}
 * that isn't one of the two types below — the {@code X-Force-Ledger-Failure} simulated fault in
 * {@link LedgerController#postCapture}, or the internal consistency check in {@code
 * LedgerPostingService#resolveMerchantId} — is intentionally left unhandled here and still
 * surfaces as an opaque 500, because those represent a genuine server-side fault or a
 * data-integrity invariant violation, not a predictable business condition a caller can act on.
 *
 * <p>The other BE-12 cases (missing/mismatched {@code X-Merchant-Id}, unknown account, unknown
 * payout) are simple, single-branch conditions already known at the controller call site — those
 * are handled by constructing {@link ErrorResponse} directly in {@code LedgerController}/{@code
 * PayoutController} rather than routed through an exception here, per this ticket's own "(or
 * throw a handled exception)" latitude.
 */
@RestControllerAdvice
public class LedgerExceptionHandler {

  @ExceptionHandler(MerchantPayableAccountNotFoundException.class)
  public ResponseEntity<ErrorResponse> handleAccountNotFound(
      MerchantPayableAccountNotFoundException e, HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(ErrorResponse.of(e.getMessage(), HttpStatus.NOT_FOUND.value(), request.getRequestURI()));
  }

  @ExceptionHandler(NoPayoutBalanceAvailableException.class)
  public ResponseEntity<ErrorResponse> handleNoBalanceAvailable(
      NoPayoutBalanceAvailableException e, HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(ErrorResponse.of(e.getMessage(), HttpStatus.CONFLICT.value(), request.getRequestURI()));
  }
}
