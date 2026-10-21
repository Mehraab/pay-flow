package dev.payflow.paymentcommand.web;

import dev.payflow.common.domain.Currency;
import dev.payflow.common.domain.Money;
import dev.payflow.cqrs.bus.CommandBus;
import dev.payflow.paymentcommand.command.CapturePaymentCommand;
import dev.payflow.paymentcommand.command.CreatePaymentCommand;
import dev.payflow.paymentcommand.command.FailPaymentCommand;
import dev.payflow.paymentcommand.command.RefundPaymentCommand;
import dev.payflow.paymentcommand.domain.PaymentId;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Rule 2 strictly enforced: depends on {@link CommandBus} only — never on {@code
 * AggregateRepository} or a handler directly. Every write here is dispatch-and-return, nothing
 * more.
 */
@RestController
public class PaymentCommandController {

  private final CommandBus commandBus;

  public PaymentCommandController(CommandBus commandBus) {
    this.commandBus = commandBus;
  }

  @PostMapping("/payments")
  public ResponseEntity<CreatePaymentResponse> create(
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @RequestHeader(value = "X-Force-Ledger-Failure", required = false) Boolean forceLedgerFailureHeader,
      @RequestBody CreatePaymentRequest request) {
    Money amount = Money.of(request.amountMinor(), Currency.valueOf(request.currency()));
    boolean forceLedgerFailure =
        Boolean.TRUE.equals(forceLedgerFailureHeader) || Boolean.TRUE.equals(request.forceLedgerFailure());
    PaymentId id =
        commandBus.dispatch(
            new CreatePaymentCommand(
                request.merchantId(),
                amount,
                request.cardNumber(),
                forceLedgerFailure,
                idempotencyKey,
                request.customer()));
    return ResponseEntity.status(201).body(new CreatePaymentResponse(id.value()));
  }

  @PostMapping("/internal/payments/{id}/capture")
  public ResponseEntity<Void> capture(@PathVariable UUID id, @RequestBody CaptureRequest request) {
    commandBus.dispatch(new CapturePaymentCommand(new PaymentId(id), request.providerRef()));
    return ResponseEntity.ok().build();
  }

  @PostMapping("/internal/payments/{id}/fail")
  public ResponseEntity<Void> fail(@PathVariable UUID id, @RequestBody FailRequest request) {
    commandBus.dispatch(new FailPaymentCommand(new PaymentId(id), request.declineReason()));
    return ResponseEntity.ok().build();
  }

  @PostMapping("/internal/payments/{id}/refund")
  public ResponseEntity<Void> refundInternal(@PathVariable UUID id) {
    commandBus.dispatch(new RefundPaymentCommand(new PaymentId(id)));
    return ResponseEntity.ok().build();
  }

  @PostMapping("/payments/{id}/refund")
  public ResponseEntity<Void> refund(@PathVariable UUID id) {
    commandBus.dispatch(new RefundPaymentCommand(new PaymentId(id)));
    return ResponseEntity.ok().build();
  }
}
