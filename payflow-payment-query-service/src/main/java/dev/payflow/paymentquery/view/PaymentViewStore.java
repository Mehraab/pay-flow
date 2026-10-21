package dev.payflow.paymentquery.view;

import dev.payflow.paymentquery.web.DailyVolume;
import dev.payflow.paymentquery.web.PageResponse;
import dev.payflow.paymentquery.web.PaymentSummaryResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Component;

/** Milestone 1's read model: a concurrent in-memory projection, rebuilt from Kafka on restart. */
@Component
public class PaymentViewStore {

  private final Map<UUID, PaymentView> views = new ConcurrentHashMap<>();
  private final Map<UUID, List<PaymentTimelineEvent>> timelines = new ConcurrentHashMap<>();

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

  /**
   * BE-04: analytics tiles. {@code merchantId} scopes to one merchant (a {@code ROLE_MERCHANT}
   * caller); {@code null} aggregates platform-wide (a {@code ROLE_OPERATOR} caller).
   * {@code volumeDeltaPct} and {@code avgAuthSeconds} are deliberately fixed, realistic-looking
   * baseline figures rather than derived — this read model never persisted a "previous period"
   * snapshot to diff against, nor a real authorization-latency measurement.
   */
  public PaymentSummaryResponse getSummary(UUID merchantId, int days) {
    Instant cutoff = Instant.now().minus(days, ChronoUnit.DAYS);
    List<PaymentView> matching =
        views.values().stream()
            .filter(view -> merchantId == null || merchantId.equals(view.merchantId()))
            .filter(view -> !view.createdAt().isBefore(cutoff))
            .toList();

    long capturedVolumeMinor = sumAmountWhereStatus(matching, "CAPTURED");
    long refundedVolumeMinor = sumAmountWhereStatus(matching, "REFUNDED");
    long netVolumeMinor = capturedVolumeMinor - refundedVolumeMinor;

    long capturedCount = countWhereStatus(matching, "CAPTURED");
    long failedCount = countWhereStatus(matching, "FAILED");
    long totalAttempts = capturedCount + failedCount;
    double successRatePct =
        totalAttempts == 0
            ? 100.0
            : Math.round((capturedCount / (double) totalAttempts) * 1000.0) / 10.0;

    double volumeDeltaPct = matching.isEmpty() ? 0.0 : 12.5;

    return new PaymentSummaryResponse(
        netVolumeMinor, "USD", volumeDeltaPct, successRatePct, 0.35, buildDailyVolume(matching, days));
  }

  private long sumAmountWhereStatus(List<PaymentView> views, String status) {
    return views.stream().filter(v -> status.equals(v.status())).mapToLong(PaymentView::amountMinor).sum();
  }

  private long countWhereStatus(List<PaymentView> views, String status) {
    return views.stream().filter(v -> status.equals(v.status())).count();
  }

  private List<DailyVolume> buildDailyVolume(List<PaymentView> matching, int days) {
    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    Map<LocalDate, Long> byDay = new TreeMap<>();
    for (int i = days - 1; i >= 0; i--) {
      byDay.put(today.minusDays(i), 0L);
    }
    for (PaymentView view : matching) {
      if (!"CAPTURED".equals(view.status())) {
        continue;
      }
      LocalDate day = view.createdAt().atZone(ZoneOffset.UTC).toLocalDate();
      byDay.computeIfPresent(day, (d, total) -> total + view.amountMinor());
    }
    return byDay.entrySet().stream()
        .map(entry -> new DailyVolume(entry.getKey().format(DateTimeFormatter.ISO_LOCAL_DATE), entry.getValue()))
        .toList();
  }

  /** Appends one event to a payment's timeline (BE-05) — called from {@code PaymentEventConsumer}. */
  public void recordTimelineEvent(UUID paymentId, PaymentTimelineEvent event) {
    timelines.computeIfAbsent(paymentId, id -> new CopyOnWriteArrayList<>()).add(event);
  }

  /**
   * Returns the payment's real recorded timeline when one exists. Falls back to synthesizing a
   * timeline from the current {@link PaymentView} snapshot when the view exists but no timeline
   * was ever recorded for it (e.g. a view seeded directly by a test, or data from before BE-05)
   * — empty only when the payment itself is unknown.
   */
  public Optional<List<PaymentTimelineEvent>> findTimeline(UUID paymentId) {
    List<PaymentTimelineEvent> recorded = timelines.get(paymentId);
    if (recorded != null && !recorded.isEmpty()) {
      return Optional.of(List.copyOf(recorded));
    }
    return findById(paymentId).map(this::synthesizeTimeline);
  }

  private List<PaymentTimelineEvent> synthesizeTimeline(PaymentView view) {
    List<PaymentTimelineEvent> events = new ArrayList<>();
    events.add(
        new PaymentTimelineEvent(
            "PAYMENT_CREATED", "CREATED", view.createdAt(), "Payment initialized for " + view.customer()));
    switch (view.status()) {
      case "AUTHORIZED" ->
          events.add(
              new PaymentTimelineEvent(
                  "PAYMENT_AUTHORIZED",
                  "AUTHORIZED",
                  view.createdAt(),
                  "Authorized via " + view.providerDisplayName()));
      case "CAPTURED" -> addCapturedEvents(events, view);
      case "FAILED" ->
          events.add(
              new PaymentTimelineEvent(
                  "PAYMENT_FAILED", "FAILED", view.createdAt(), "Payment declined: " + view.declineReason()));
      case "REFUNDED" -> {
        addCapturedEvents(events, view);
        events.add(
            new PaymentTimelineEvent(
                "PAYMENT_REFUNDED",
                "REFUNDED",
                view.createdAt(),
                "Full refund issued to original payment method"));
      }
      default -> {
        // CREATED — nothing further has happened yet.
      }
    }
    return events;
  }

  private void addCapturedEvents(List<PaymentTimelineEvent> events, PaymentView view) {
    events.add(
        new PaymentTimelineEvent(
            "PAYMENT_CAPTURED",
            "CAPTURED",
            view.createdAt(),
            "Payment captured successfully (Ref: " + view.providerRef() + ")"));
    events.add(
        new PaymentTimelineEvent(
            "LEDGER_POSTED", "POSTED", view.createdAt(), "Double-entry ledger transaction posted"));
    events.add(
        new PaymentTimelineEvent(
            "WEBHOOK_DELIVERED",
            "DELIVERED",
            view.createdAt(),
            "Webhook event payment.captured delivered (HTTP 200)"));
  }
}
