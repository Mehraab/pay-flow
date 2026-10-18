package dev.payflow.paymentcommand.command;

import dev.payflow.cqrs.bus.CommandHandler;
import dev.payflow.cqrs.persistence.AggregateRepository;
import dev.payflow.paymentcommand.domain.Payment;
import dev.payflow.paymentcommand.domain.PaymentDomainEvent;
import dev.payflow.paymentcommand.domain.PaymentId;
import org.springframework.stereotype.Component;

@Component
public class CapturePaymentHandler implements CommandHandler<CapturePaymentCommand, Void> {

  private final AggregateRepository<Payment, PaymentId, PaymentDomainEvent> repository;

  public CapturePaymentHandler(AggregateRepository<Payment, PaymentId, PaymentDomainEvent> repository) {
    this.repository = repository;
  }

  @Override
  public Void handle(CapturePaymentCommand command) {
    Payment payment = repository.getById(command.id());
    payment.capture(command.providerRef());
    repository.save(payment);
    return null;
  }
}
