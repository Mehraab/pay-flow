package dev.payflow.paymentquery.view;

import dev.payflow.paymentquery.web.PageResponse;
import java.util.Comparator;
import java.util.List;
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

  /**
   * No-op when the payment hasn't been seen yet (its {@code payment.created} event is missing).
   * {@code providerRef}/{@code providerDisplayName} are only overwritten when non-null — a
   * refund (which carries neither) must not blank out what capture/authorize already recorded.
   */
  public void updateStatus(UUID paymentId, String status, String providerRef, String providerDisplayName) {
    views.computeIfPresent(
        paymentId,
        (id, existing) ->
            new PaymentView(
                existing.paymentId(),
                existing.merchantId(),
                existing.amountMinor(),
                existing.currency(),
                status,
                providerRef != null ? providerRef : existing.providerRef(),
                existing.declineReason(),
                existing.createdAt(),
                existing.customer(),
                existing.paymentMethod(),
                existing.idempotencyKey(),
                providerDisplayName != null ? providerDisplayName : existing.providerDisplayName()));
  }

  /** No-op when the payment hasn't been seen yet (its {@code payment.created} event is missing). */
  public void updateFailed(UUID paymentId, String declineReason) {
    views.computeIfPresent(
        paymentId,
        (id, existing) ->
            new PaymentView(
                existing.paymentId(),
                existing.merchantId(),
                existing.amountMinor(),
                existing.currency(),
                "FAILED",
                existing.providerRef(),
                declineReason,
                existing.createdAt(),
                existing.customer(),
                existing.paymentMethod(),
                existing.idempotencyKey(),
                existing.providerDisplayName()));
  }

  public Optional<PaymentView> findById(UUID paymentId) {
    return Optional.ofNullable(views.get(paymentId));
  }

  /**
   * BE-02: list/search for the console. {@code merchantId} scopes a merchant caller to their own
   * payments (the gateway only forwards {@code X-Merchant-Id} for {@code ROLE_MERCHANT} tokens);
   * an operator caller passes {@code null} and sees everything.
   */
  public PageResponse<PaymentView> findPayments(UUID merchantId, String status, String q, int page, int size) {
    List<PaymentView> matching =
        views.values().stream()
            .filter(view -> merchantId == null || merchantId.equals(view.merchantId()))
            .filter(view -> status == null || status.equalsIgnoreCase(view.status()))
            .filter(view -> q == null || matchesQuery(view, q))
            .sorted(Comparator.comparing(PaymentView::createdAt).reversed())
            .toList();

    long totalElements = matching.size();
    int totalPages = size > 0 ? (int) Math.ceil((double) totalElements / size) : 0;
    int fromIndex = Math.min(page * size, matching.size());
    int toIndex = Math.min((page + 1) * size, matching.size());
    List<PaymentView> content = fromIndex < toIndex ? matching.subList(fromIndex, toIndex) : List.of();

    return new PageResponse<>(content, page, size, totalElements, totalPages);
  }

  private boolean matchesQuery(PaymentView view, String q) {
    String needle = q.toLowerCase();
    return view.paymentId().toString().toLowerCase().contains(needle)
        || (view.customer() != null && view.customer().toLowerCase().contains(needle));
  }
}
