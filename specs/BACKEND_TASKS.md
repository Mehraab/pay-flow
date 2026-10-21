# PayFlow — Backend Task Backlog

Derived from the gap analysis in [`API_UI_MAP.md`](./API_UI_MAP.md) against
[`API_CATALOG.md`](./API_CATALOG.md). Each ticket cites the console view(s) it
unblocks and the severity markers (🔴/🟠/🟡) from the map. Ordered so the Angular
frontend plan (`FRONTEND_TASKS.md`) can start wiring real calls as early as possible —
gateway/CORS first, since every other ticket is unreachable from a browser without it.

---

## BE-01 — Gateway routing & CORS (`payflow-api-gateway`)
**Unblocks**: every view in the console; nothing is browser-reachable today.

- Add a `WebMvcConfigurer`/CORS filter allowing `http://localhost:4200` (Angular dev
  server) — origin, `GET/POST/PATCH`, headers `Authorization, Content-Type,
  Idempotency-Key, X-Force-Ledger-Failure`, credentials as needed. **Catalog confirms
  no CORS config exists anywhere today** — this is a hard blocker, not an enhancement.
- Add proxy routes for `payflow-ledger-service` (currently only
  `/operator/ledger/integrity` is proxied):
  - `GET /accounts/{accountName}/balance`
  - `GET /accounts/{merchantId}/{accountName}/balance`
  - `GET /ledger?transaction={transactionRef}`
  - `POST /payouts`, `GET /payouts/{id}`
  - `POST /payouts/sweep` — route under `/operator/**` (batch/operator-shaped action
    per the Catalog's own note), not left open.
- Decide and document the role gate for the newly-proxied ledger/payout routes (the
  merchant Ledger view needs `ROLE_MERCHANT`; the sweep action should be
  `ROLE_OPERATOR`-only).

**Severity**: 🔴/🟠 (map §4 "Ledger", §5 "Ledger Integrity" section-wide gap note).

---

## BE-02 — Payment list/search endpoint (`payflow-payment-query-service`)
**Unblocks**: Overview's "Recent transactions" table, the entire Transactions view
(status filter chips + global search).

- Implement `GET /api/v1/payments` on the query service, gateway-routed, `ROLE_MERCHANT`
  (scoped to the caller's `X-Merchant-Id`) or `ROLE_OPERATOR` (unscoped).
- Query params: `status` (enum filter), `q` (free-text match against `paymentId` and —
  once BE-03 lands — `customer`), `page`/`size` for pagination.
- Response: `Page<PaymentView>` using the enriched DTO from BE-03.
- **Gap note to close**: today only `POST /api/v1/payments` (create) and
  `GET /api/v1/payments/{id}` (single) exist — there is no list route at all, gateway or
  otherwise.

**Severity**: 🔴 (map §1 "Overview", §2 "Transactions" — the single biggest gap; two
full views have no backing route).

---

## BE-03 — Enrich `PaymentView` DTO
**Unblocks**: Transactions table columns (Customer, Method, Created), Transaction
Detail fields (`dCustomer`, `dMethod`, `dCreated`, `dIdem`, `dProvider`).

- Add to `PaymentView`:
  - `createdAt` (timestamp) — currently absent entirely.
  - `customer` (name/email) — currently absent entirely.
  - `paymentMethod` — masked `{brand, last4}`, derived at capture time from the raw
    `cardNumber` already received by the command service (never persist/re-expose the
    PAN itself).
  - `idempotencyKey` — echo back the key the command service received on create.
  - `providerDisplayName` — resolve `providerRef` against the provider-gateway
    service's config so the UI isn't given a raw reference string. (Alternative: leave
    this to the frontend via a lookup against `GET /operator/providers`, but that route
    is `ROLE_OPERATOR`-only today, so merchants can't resolve it client-side — doing the
    lookup server-side in the query service is the cleaner fix.)
- Confirm/document the `status` vocabulary (`PENDING/CAPTURED/DECLINED/REFUNDED`) as the
  contract the frontend maps display labels onto — no backend change needed here beyond
  writing it down, but flag it so the frontend doesn't invent its own casing.

**Severity**: 🟡 (map §2 "Transactions" table columns, §3 "Transaction Detail" — five
separate missing-field rows).

---

## BE-04 — Analytics/summary endpoint (`payflow-payment-query-service`)
**Unblocks**: Overview's stat tiles (net volume, success rate, avg. auth time) and the
14-day volume chart.

- Implement `GET /api/v1/payments/summary?days=30` (or similar), gateway-routed,
  `ROLE_MERCHANT`/`ROLE_OPERATOR`.
- Response: `{ netVolumeMinor, currency, volumeDeltaPct, successRatePct, avgAuthSeconds,
  dailyVolume: [{date, amountMinor}] }`.
- **Explicitly out of scope for this ticket**: "Active disputes" — disputes are not a
  modeled concept anywhere in the system (no entity, no status value). File as a
  separate product discussion, not an API gap to close here.
- **Note**: `avgAuthSeconds` requires capturing authorization timing somewhere in the
  saga (command service or orchestrator) — no such timestamp exists today. Flag as a
  dependency, not a one-line addition.

**Severity**: 🔴 (map §1 "Overview" — four of five stat tiles plus the chart).

---

## BE-05 — Per-payment event timeline
**Unblocks**: Transaction Detail's timeline (`dTimeline`: created → authorized →
captured → ledger entry posted → webhook delivered → refund requested → refunded).

- Add a `GET /api/v1/payments/{id}/events` endpoint backed by a new append-only
  `payment_events` record (or by correlating the orchestrator's Temporal workflow
  history, keyed by `workflowId`, back to `paymentId` — the Catalog currently gives no
  mechanism to do that join).
- Decide ownership: this spans command service (payment lifecycle), ledger service
  (posting), and notification service (webhook delivery) — likely needs the
  orchestrator to be the aggregation point since it already observes all three legs of
  the saga.

**Severity**: 🔴 (map §3 "Transaction Detail" — no endpoint returns a step-by-step
audit trail today; `/operator/saga-events` is a different shape entirely and
operator-only).

---

## BE-06 — Platform-wide ledger feed
**Unblocks**: Ledger Integrity's "Platform double-entry log" table (all merchants).

- Add `GET /operator/ledger/entries?since=...&page=...` on `payflow-ledger-service`,
  gateway-routed under `/operator/**` (`ROLE_OPERATOR`).
- Also add a `status` field to `LedgerEntryResponse` (the UI renders a "Posted" column
  that has nothing to bind to today) — apply this fix to the existing
  `GET /ledger?transaction=...` response shape too, once BE-01 routes it.

**Severity**: 🔴 (map §5 — "missing from the ledger service's surface entirely, not
just missing from the gateway," bigger gap than the per-transaction route in §4).

---

## BE-07 — Provider health/metrics fields
**Unblocks**: Provider Health's status pill, success-rate, and avg-latency stats.

- Add `healthStatus` (`HEALTHY`/`DEGRADED`), `successRatePct`, `avgLatencyMs` to
  `ProviderConfig`, sourced from whatever the provider-gateway service already tracks
  per-call (or a new rolling-window aggregator if it doesn't track this yet — confirm
  with the service owner before scoping this as "just add a field").
- No route change needed — `GET /operator/providers` is already gateway-routed and
  `ROLE_OPERATOR`-gated; this is a response-schema addition only.

**Severity**: 🟡 (map §6 — two missing field groups on an otherwise clean match).

---

## BE-08 — Webhook delivery log (merchant-facing, full history)
**Unblocks**: Developers view's "Recent deliveries" table.

- Current `GET /operator/webhooks/dlq` is `ROLE_OPERATOR`-only and DLQ-only
  (dead-lettered deliveries), but the Developers view sits under merchant-only nav and
  shows a mixed successful/failed log — a fundamentally different endpoint, not a role
  tweak on the existing one.
- Add `GET /webhooks/deliveries` scoped by `X-Merchant-Id`, `ROLE_MERCHANT`, returning
  all deliveries (success and failure) for that merchant's endpoints.
- Requires `payflow-notification-service` to persist successful deliveries too, not
  just DLQ entries — confirm current retention before committing to this scope.

**Severity**: 🔴🟡 (map §8 — role mismatch plus wrong-shape data, flagged as two
compounding issues in the source map).

---

## Explicitly deferred — product decisions, not backend tickets

These came up in the gap analysis but need a product/design decision before they're
actionable as backend work; listed here so they aren't silently dropped, matching the
map's "Summary of gaps" section:

- **API-key management** (Developers view's Test/Live key pairs) — the auth model is
  JWT bearer-token, issued per role, with no concept of long-lived per-merchant key
  pairs. Adding this is a new auth subsystem, not a DTO fix.
- **Test/Live environment split** — no backend concept anywhere (no header, no scoping,
  no separate base URL). Needs a decision on what "Live" vs "Test" even means for this
  processor before an API is designed.
- **Merchant webhook-URL registration** — no endpoint exists to let a merchant register
  their own callback URL; `/internal/webhooks/dispatch` is orchestrator-internal only.
- **Active disputes** — not modeled anywhere (see BE-04 note).

## Explicitly not a gap — do not ticket

- `POST /api/v1/payments` (payment creation) is correctly absent from the console by
  design — it's called server-to-server by the merchant's own site/backend, never by
  this dashboard. Don't add a "wire up checkout" ticket.
