package dev.payflow.providergateway;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Raised by the {@code 0119} card-suffix branch — surfaces as HTTP 504 to the caller. */
@ResponseStatus(HttpStatus.GATEWAY_TIMEOUT)
public class ProviderTimeoutException extends RuntimeException {

  public ProviderTimeoutException(String message) {
    super(message);
  }
}
