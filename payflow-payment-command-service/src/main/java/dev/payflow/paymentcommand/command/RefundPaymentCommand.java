package dev.payflow.paymentcommand.command;

import dev.payflow.cqrs.bus.Command;
import dev.payflow.paymentcommand.domain.PaymentId;

public record RefundPaymentCommand(PaymentId id) implements Command<Void> {}
