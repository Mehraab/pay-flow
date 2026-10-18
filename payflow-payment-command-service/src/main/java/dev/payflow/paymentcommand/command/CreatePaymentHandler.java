package dev.payflow.paymentcommand.command;

import dev.payflow.cqrs.bus.CommandHandler;
import dev.payflow.cqrs.persistence.AggregateRepository;
import dev.payflow.paymentcommand.domain.Payment;
import dev.payflow.paymentcommand.domain.PaymentDomainEvent;
import dev.payflow.paymentcommand.domain.PaymentId;
import org.springframework.stereotype.Component;

@Component
public class CreatePaymentHandler implements CommandHandler<CreatePaymentCommand, PaymentId> {

  private final AggregateRepository<Payment, PaymentId, PaymentDomainEvent> repository;

  public CreatePaymentHandler(AggregateRepository<Payment, PaymentId, PaymentDomainEvent> repository) {
    this.repository = repository;
  }

  @Override
  public PaymentId handle(CreatePaymentCommand command) {
    Payment payment =
        Payment.create(
            PaymentId.generate(), command.merchantId(), command.amount(), command.idempotencyKey());
    repository.save(payment);
    return payment.id();
  }
}
