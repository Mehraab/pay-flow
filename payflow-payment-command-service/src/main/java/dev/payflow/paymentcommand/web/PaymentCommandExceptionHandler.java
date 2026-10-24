package dev.payflow.paymentcommand.web;

import dev.payflow.common.dto.ErrorResponse;
import dev.payflow.cqrs.exception.AggregateNotFoundException;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * BE-12: maps known, client-triggerable domain failures to a proper status and a uniform {@link
 * ErrorResponse} body. {@code IllegalStateException} covers {@code Payment}'s own
 * state-transition guards (invalid capture/fail/refund) and {@code RedisIdempotencyInterceptor}'s
 * concurrent-duplicate-request guard — every one a real, predictable 409, not a server fault, so
 * catching it broadly here (unlike {@code payflow-ledger-service}'s narrower advice) is safe: this
 * service has no deliberately-opaque-500 simulated fault the way ledger's {@code
 * X-Force-Ledger-Failure} does.
 *
 * <p>{@code AggregateNotFoundException} — not {@code NoSuchElementException} — is what {@code
 * CapturePaymentHandler}/{@code FailPaymentHandler}/{@code RefundPaymentHandler} actually throw
 * for "payment not found" (via {@code AggregateRepository#getById}'s default implementation);
 * {@code NoSuchElementException} is handled too for parity with the other services' advice, even
 * though nothing in this service throws it today.
 */
@RestControllerAdvice
public class PaymentCommandExceptionHandler {

  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<ErrorResponse> handleInvalidStateTransition(IllegalStateException e) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of(e.getMessage(), HttpStatus.CONFLICT.value()));
  }

  @ExceptionHandler({AggregateNotFoundException.class, NoSuchElementException.class})
  public ResponseEntity<ErrorResponse> handleNotFound(RuntimeException e) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse.of(e.getMessage(), HttpStatus.NOT_FOUND.value()));
  }
}
