package dev.payflow.paymentquery.view;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** Milestone 1's read model: a concurrent in-memory projection, rebuilt from Kafka on restart. */
@Component
public class PaymentViewStore {

  private final Map<UUID, PaymentView> views = new ConcurrentHashMap<>();

  public void save(PaymentView view) {
    views.put(view.paymentId(), view);
  }

  /** No-op when the payment hasn't been seen yet (its {@code payment.created} event is missing). */
  public void updateStatus(UUID paymentId, String status, String providerRef) {
    views.computeIfPresent(
        paymentId,
        (id, existing) ->
            new PaymentView(
                existing.paymentId(),
                existing.merchantId(),
                existing.amountMinor(),
                existing.currency(),
                status,
                providerRef != null ? providerRef : existing.providerRef()));
  }

  public Optional<PaymentView> findById(UUID paymentId) {
    return Optional.ofNullable(views.get(paymentId));
  }
}
