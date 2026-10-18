package dev.payflow.paymentcommand.command;

import dev.payflow.cqrs.bus.Command;
import dev.payflow.paymentcommand.domain.PaymentId;

public record CapturePaymentCommand(PaymentId id, String providerRef) implements Command<Void> {}
