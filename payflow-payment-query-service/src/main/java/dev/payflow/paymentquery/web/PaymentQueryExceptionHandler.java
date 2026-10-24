package dev.payflow.paymentquery.web;

import dev.payflow.common.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * BE-12: uniform 404 for any resource-lookup failure signalled via {@code
 * NoSuchElementException} elsewhere in this service. {@link PaymentQueryController}'s own known
 * not-found cases (payment, timeline) are handled inline with a structured {@link ErrorResponse}
 * at the call site instead, since {@code PaymentViewStore} reports "not found" via {@code
 * Optional}, not by throwing — this advice is the same defensive, uniform-shape net every other
 * PayFlow service carries.
 */
@RestControllerAdvice
public class PaymentQueryExceptionHandler {

  @ExceptionHandler(NoSuchElementException.class)
  public ResponseEntity<ErrorResponse> handleNotFound(NoSuchElementException e, HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(ErrorResponse.of(e.getMessage(), HttpStatus.NOT_FOUND.value(), request.getRequestURI()));
  }
}
