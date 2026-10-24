package dev.payflow.common.dto;

import java.time.Instant;

/**
 * Platform-wide uniform error response body (BE-12). Preserves "error" as the primary message
 * property and avoids leaking internal gateway topology paths.
 */
public record ErrorResponse(String error, int status, Instant timestamp) {

  public static ErrorResponse of(String error, int status) {
    return new ErrorResponse(error, status, Instant.now());
  }
}
