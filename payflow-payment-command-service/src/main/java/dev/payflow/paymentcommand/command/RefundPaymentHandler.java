package dev.payflow.paymentcommand.command;

import dev.payflow.cqrs.bus.CommandHandler;
import dev.payflow.cqrs.persistence.AggregateRepository;
import dev.payflow.paymentcommand.domain.Payment;
import dev.payflow.paymentcommand.domain.PaymentDomainEvent;
import dev.payflow.paymentcommand.domain.PaymentId;
import org.springframework.stereotype.Component;

@Component
public class RefundPaymentHandler implements CommandHandler<RefundPaymentCommand, Void> {

  private final AggregateRepository<Payment, PaymentId, PaymentDomainEvent> repository;

  public RefundPaymentHandler(AggregateRepository<Payment, PaymentId, PaymentDomainEvent> repository) {
    this.repository = repository;
  }

  @Override
  public Void handle(RefundPaymentCommand command) {
    Payment payment = repository.getById(command.id());
    payment.refund();
    repository.save(payment);
    return null;
  }
}
