package dev.payflow.paymentcommand.command;

import dev.payflow.common.domain.Money;
import dev.payflow.cqrs.bus.IdempotentCommand;
import dev.payflow.paymentcommand.domain.PaymentId;
import java.util.UUID;

public record CreatePaymentCommand(UUID merchantId, Money amount, String idempotencyKey)
    implements IdempotentCommand<PaymentId> {

  @Override
  public Class<PaymentId> resultType() {
    return PaymentId.class;
  }
}
