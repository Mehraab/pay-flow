package dev.payflow.common.dto;

import java.time.Instant;

/**
 * Platform-wide uniform error response body (BE-12). Preserves "error" as the primary message
 * property for full backward compatibility with existing frontend and gateway auth error
 * parsing.
 */
public record ErrorResponse(String error, int status, String path, Instant timestamp) {

  public static ErrorResponse of(String error, int status, String path) {
    return new ErrorResponse(error, status, path, Instant.now());
  }
}
