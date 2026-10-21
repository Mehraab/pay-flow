package dev.payflow.ledger.consumer;

import java.util.UUID;

/**
 * Only the field this consumer needs from {@code payment.refunded}'s JSON body: the event's own
 * unique id, used for the {@code processed_events} dedup check — the payload carries no
 * merchantId or amount (see {@code PaymentRefundedConsumer}'s Javadoc), and neither of those is
 * needed here anyway: {@code LedgerPostingService.reverseCaptureIfExists} derives both from the
 * existing {@code ledger_entries} rows instead.
 */
record PaymentRefundedPayload(Metadata metadata) {

  record Metadata(UUID eventId) {}
}
