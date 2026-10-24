package dev.payflow.notification.web;

import dev.payflow.common.dto.ErrorResponse;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * BE-12: maps known failures to a proper status and a uniform {@link ErrorResponse} body.
 * {@code MissingRequestHeaderException} covers {@link NotificationController#getMerchantDeliveries}'s
 * required {@code X-Merchant-Id} header; {@code NoSuchElementException} covers {@link
 * WebhookDlqController#retry}'s unknown-delivery-id path.
 */
@RestControllerAdvice
public class NotificationExceptionHandler {

  @ExceptionHandler(MissingRequestHeaderException.class)
  public ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException e) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(ErrorResponse.of(e.getMessage(), HttpStatus.BAD_REQUEST.value()));
  }

  @ExceptionHandler(NoSuchElementException.class)
  public ResponseEntity<ErrorResponse> handleNotFound(NoSuchElementException e) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse.of(e.getMessage(), HttpStatus.NOT_FOUND.value()));
  }
}
