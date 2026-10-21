package dev.payflow.paymentcommand.command;

import dev.payflow.cqrs.bus.CommandHandler;
import dev.payflow.cqrs.persistence.AggregateRepository;
import dev.payflow.orchestrator.workflow.PaymentWorkflowInput;
import dev.payflow.paymentcommand.domain.Payment;
import dev.payflow.paymentcommand.domain.PaymentDomainEvent;
import dev.payflow.paymentcommand.domain.PaymentId;
import dev.payflow.paymentcommand.workflow.PaymentWorkflowStarter;
import org.springframework.stereotype.Component;

@Component
public class CreatePaymentHandler implements CommandHandler<CreatePaymentCommand, PaymentId> {

  private final AggregateRepository<Payment, PaymentId, PaymentDomainEvent> repository;
  private final PaymentWorkflowStarter workflowStarter;

  public CreatePaymentHandler(
      AggregateRepository<Payment, PaymentId, PaymentDomainEvent> repository,
      PaymentWorkflowStarter workflowStarter) {
    this.repository = repository;
    this.workflowStarter = workflowStarter;
  }

  @Override
  public PaymentId handle(CreatePaymentCommand command) {
    Payment payment =
        Payment.create(
            PaymentId.generate(), command.merchantId(), command.amount(), command.idempotencyKey());
    repository.save(payment);
    workflowStarter.start(
        new PaymentWorkflowInput(
            payment.id().value(),
            command.merchantId(),
            command.amount(),
            command.cardNumber(),
            command.forceLedgerFailure()));
    return payment.id();
  }
}
