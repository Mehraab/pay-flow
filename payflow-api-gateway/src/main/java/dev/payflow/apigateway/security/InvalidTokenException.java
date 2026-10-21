package dev.payflow.apigateway.security;

/** Thrown when a refresh token is absent, expired, or already revoked. */
public class InvalidTokenException extends RuntimeException {

  public InvalidTokenException(String message) {
    super(message);
  }
}
