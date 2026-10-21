package dev.payflow.common.dto;

import dev.payflow.common.domain.Money;
import java.util.UUID;

/** API Gateway → Provider Gateway, and Temporal's {@code authorizeAndCapture} Activity. */
public record AuthorizePaymentRequest(UUID paymentId, Money amount, String cardNumber) {}
