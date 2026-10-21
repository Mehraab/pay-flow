package dev.payflow.orchestrator.workflow;

import dev.payflow.common.domain.Money;
import java.util.UUID;

public record PaymentWorkflowInput(
    UUID paymentId, UUID merchantId, Money amount, String cardNumber, boolean forceLedgerFailure) {}
