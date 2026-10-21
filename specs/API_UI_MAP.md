# PayFlow Console → Gateway API Map

**Second pass.** Re-derived from the actual backend source (not the possibly-stale
`specs/API_CATALOG.md`) after commits `99c3634` (BE-01/02/03) and `dd786ac`
(BE-04..BE-08), and cross-checked against the real Angular frontend now under
`frontend/src/app/` (which did not exist on the first pass — only the static
`PayFlow Console.html` mock did). Most of the gateway-side gaps from the first pass are
now closed. The new, more interesting gap is that **the frontend's service/model layer
was written against stub assumptions before BE-01..08 shipped, and hasn't been updated
to match what actually landed** — that's now the main body of this report.

**Architecture note (unchanged)**: PayFlow is the payment *processor*. The merchant's
own website calls `POST /api/v1/payments` server-to-server with the card data — this
console never does. The console is the merchant/operator dashboard: read-mostly views
over payments/ledger/platform-health, plus `POST /api/v1/payments/{id}/refund` as a
write action. The new Angular router confirms this directly: `frontend/src/app/app.routes.ts`
has a `sandbox` route labeled *"Internal engineering tool — not the real checkout
path"* — i.e. even PayFlow's own team treats payment-creation as a separate,
clearly-flagged dev tool, not a console feature.

Severity key: 🔴 no backend route exists · 🟠 route exists but not gateway-routed ·
🟡 route exists and is gateway-routed but response schema is missing fields /
diverges from the frontend's model · 🟢 clean match, actually wired · ⚪ wired on the
backend, but the frontend still calls a mock/stub instead of the real endpoint.

---

## 0. What changed on the backend since the first pass

| New/changed route | Service | Notes |
|---|---|---|
| `GET /api/v1/payments` (list) | query-service, **now gateway-routed** | Filters: `status`, `q` (matches payment ID or `customer`), `page`, `size`. Merchant-scoped via `X-Merchant-Id` (operator sees all). Closes the §1/§2 "no list endpoint" gap from the first pass. |
| `GET /api/v1/payments/summary` | query-service, **new, gateway-routed** | `{netVolumeMinor, currency, volumeDeltaPct, successRatePct, avgAuthSeconds, dailyVolume[]}`. Closes the Overview stats/chart gap — **except** `volumeDeltaPct` and `avgAuthSeconds` are hardcoded constants (12.5% / 0.35s) in `PaymentViewStore.getSummary`, not derived — fine for now, but not "real" analytics. No `activeDisputes` field exists anywhere — that stat is still unbacked. |
| `GET /api/v1/payments/{id}/events` | query-service, **new, gateway-routed** | `[{eventType, status, timestamp, description}]`. Closes the Detail-view timeline gap almost exactly as the mock envisioned (narrative `description` string included). Falls back to a synthesized timeline from the current `PaymentView` if no real events were recorded yet — never 404s for a known payment. |
| `PaymentView` response enrichment | query-service | Added `createdAt`, `customer`, `paymentMethod{brand,last4}`, `idempotencyKey`, `providerDisplayName`. Closes essentially every "missing field" gap the Detail view had in the first pass. |
| `GET /accounts/{accountName}/balance`, `GET /accounts/{merchantId}/{accountName}/balance`, `GET /ledger`, `POST /payouts`, `GET /payouts/{id}` | ledger-service, **now gateway-routed** | All of Section 3 from the old Catalog ("none of these are gateway-routed") is now proxied directly by `PaymentGatewayController`. Closes the whole Ledger-view gap. |
| `POST /operator/payouts/sweep` | ledger-service, **new, gateway-routed** | Note the path: it's `/operator/payouts/sweep`, not `/payouts/sweep` — moved under the operator prefix (as the first pass recommended) so it's `ROLE_OPERATOR`-gated. |
| `GET /operator/ledger/entries` | ledger-service, **new, gateway-routed** | `{content:[...], page, size, totalElements, totalPages}` — paginated, `since`-filterable, **platform-wide** ledger feed. Closes the "no platform-wide ledger log exists" gap from Ledger Integrity. |
| `GET /webhooks/deliveries` | notification-service, **new, gateway-routed, merchant-facing** | Every delivery (success + failure), not just DLQ — closes the Developers-page webhook-log gap. **Caveat**: the downstream controller requires `X-Merchant-Id` as a non-optional header; only a `ROLE_MERCHANT` token gets one injected, so an operator token calling this will 400. |
| `ProviderConfig` response enrichment | provider-gateway-service | Added `healthStatus`, `successRatePct`, `avgLatencyMs`. Closes the Provider Health "missing stats" gap — `GET /operator/providers` now returns everything the mock displays. |
| CORS | gateway | `CorsFilterConfiguration` now allows `http://localhost:4200` with credentials, ahead of the JWT filter so preflight `OPTIONS` isn't 401'd. Closes the CORS gap — **but note it's hardcoded to exactly that one origin**, so a different dev port or a deployed frontend origin needs this list extended. |
| `CreatePaymentRequest` gets a `customer` field | command-service | The merchant's checkout now passes a customer name/label through at creation time — this is what populates `PaymentView.customer` downstream. |

Unchanged / still gaps: no API-key endpoint, no merchant webhook-URL registration
endpoint, no "active disputes" concept, `GET /api/v1/payments/{id}` and
`GET /api/v1/payments/{id}/events` are still **not** merchant-scoped (any authenticated
merchant token can still fetch any payment or its timeline by ID — only the list/search
and summary endpoints enforce `X-Merchant-Id` scoping).

---

## 1. Frontend implementation status (the real gap now)

The Angular app under `frontend/src/app/` is being built incrementally, and its
service/model layer was authored **before** BE-01..08 landed, using `STUB(BE-xx)`
comments as placeholders. Several of those stubs are now stale: the backend shipped,
but the frontend code wasn't updated to match what it actually returns.

| Frontend piece | Real backend status | Frontend status | Gap |
|---|---|---|---|
| `AuthService.login/refreshAccessToken/revokeAndLogout` (`core/services/auth.service.ts`) | `POST /auth/token`, `/auth/refresh`, `/auth/revoke` — unchanged, already gateway-routed | 🟢 **Fully wired**, including the 401→refresh→retry flow in `auth.interceptor.ts` and real role re-issuance in `switchRole()` | None — this is the one area that's further ahead of the old static mock, which never called `/auth/token` at all. |
| `PaymentService.getPayment()` / `.refundPayment()` | `GET /api/v1/payments/{id}`, `POST /api/v1/payments/{id}/refund` | 🟢 Wired to the real endpoints | None. |
| `PaymentService.listPayments()` | `GET /api/v1/payments` **now exists and is gateway-routed** (BE-02/03) | ⚪ Still `return of(MOCK_PAYMENTS).pipe(delay(300))` — comment says `// TODO: Wire to backend once BE-02 is merged`, but BE-02 **is** merged | **Stale TODO.** `ListPaymentsFilter{status, q, page}` already matches the real query params (`status`, `q`, `page`, and the backend's `size` which the FE interface doesn't even expose yet) — this is a pure wiring task now, not blocked on backend work. |
| `PaymentService.getPaymentEvents()` | `GET /api/v1/payments/{id}/events` **now exists** (BE-05) | ⚪ Still calls `buildMockTimeline()` over `MOCK_PAYMENTS` — comment says `// TODO: Wire to backend once BE-05 is merged`, but BE-05 **is** merged | Same pattern — stale TODO, ready to wire. |
| `PaymentViewEnriched` model (`core/models/payment.model.ts`) | Real `PaymentView` now has `customer`, `paymentMethod:{brand,last4}`, `createdAt`, `idempotencyKey`, `providerDisplayName` | 🟡 Model has `customerName?`, `method?` (a single pre-formatted string like `"Visa •••• 4242"`), `createdAt?`, `idempotencyKey?` — **no `providerDisplayName`** | **Field-name/shape drift**, not just missing wiring: `customer`≠`customerName`, and the real `paymentMethod` is a `{brand, last4}` object while the FE expects one pre-joined display string. Whoever wires `listPayments`/`getPayment` to the real response will need to rename/reshape these, not just flip a stub flag. |
| `LedgerService`, `OperatorService` (`core/services/ledger.service.ts`, `operator.service.ts`) | `GET /accounts/.../balance`, `GET /ledger`, `POST /payouts`, `GET /payouts/{id}`, `POST /operator/payouts/sweep`, `GET /operator/ledger/entries`, `GET /operator/providers`, `PATCH /operator/providers/{id}/routing`, `GET /operator/saga-events`, `GET /operator/webhooks/dlq`, `GET /webhooks/deliveries` — **all exist and are gateway-routed today** | 🔴 Both service classes are empty shells (`// Methods added task-by-task (Task 5/6) as each feature needs them.`) | Not a backend gap at all — just sequencing. Every API these two services will eventually need already exists. |
| `ProviderConfig` model (`core/models/operator.model.ts`) | Real response already includes `healthStatus`, `successRatePct`, `avgLatencyMs` (BE-07) | 🟡 FE model still only has `{id, name, priority, active}`, and separately defines a **`ProviderHealth`** stub (`{providerId, successRatePct, avgLatencyMs}`) marked `STUB(BE-07) — no real endpoint exists yet` | **Stale assumption about the shape of the fix.** BE-07 didn't ship a separate health endpoint — it folded health/stats directly into `ProviderConfig`. The FE model needs updating to match (add the three fields to `ProviderConfig`, drop/repurpose the separate `ProviderHealth` interface), not just "wire up a new call." |
| `PaymentsSummary` model (`core/models/analytics.model.ts`) | Real `PaymentSummaryResponse`: `{netVolumeMinor, currency, volumeDeltaPct, successRatePct, avgAuthSeconds, dailyVolume[]}` | 🟡 FE model: `{netVolumeMinor, successRatePct, avgAuthTimeMs, dailyVolume[]}` — marked `STUB(BE-04) — no real endpoint exists yet`, also stale | **Unit mismatch, not just missing fields**: FE expects `avgAuthTimeMs` (milliseconds); backend returns `avgAuthSeconds` (seconds) — a naive wire-up would be off by 1000x. FE is also missing `currency` and `volumeDeltaPct` entirely. |
| `SagaEventResponse` model (`operator.model.ts`) | Real: `{workflowId, status, startTime, closeTime}` — unchanged | 🟢 FE model matches exactly | No drift here — already correctly scoped down from the old static mock's wishful "narrative event text," which never existed on the backend either. |
| `WebhookDeliveryResponse` model (`operator.model.ts`) | Real: same shape for both the operator DLQ list and the new merchant `GET /webhooks/deliveries` (BE-08 reuses the record) | 🟢 FE model matches; only the DLQ call is wired (presumably, under Tasks 5/6) — the new merchant-facing endpoint just needs a second method added, no model change | Minor: FE has no method at all yet for `GET /webhooks/deliveries` specifically. |
| `LedgerEntryResponse` model (`ledger.model.ts`) | Real response now also has a `status` field (`"POSTED"`, BE-06) | 🟡 FE model missing `status` | Small, easy fix once `LedgerService` is implemented. |
| `routes` for `overview`, `ledger`, `sandbox`, `developers`, `operator/*` | — | 🔴 All still `PlaceholderView` (`app.routes.ts`) | Expected at this stage — only `transactions`/`transactions/:id` and `signin` are real components so far. Listed here just so this doc's view-by-view section below (§2) isn't mistaken for "already built." |

---

## 2. Console view → Gateway API mapping (design-mock baseline, now mostly backed)

This keeps the same per-view structure as the first pass, against the original
`PayFlow Console.html` design mock, since that's still the product spec the real
frontend is being built toward (per the route list in §1, most of these views aren't
implemented in Angular yet — they're `PlaceholderView`). Status now reflects the
**backend**, not the frontend.

### Overview
- Stat cards (net volume, success rate, avg. auth time) + 14-day chart → `GET /api/v1/payments/summary`. 🟢 Backend-complete.
- "Active disputes" stat → 🔴 still no concept anywhere.
- "Recent transactions" (6 rows) → `GET /api/v1/payments?size=6` (or client-side slice of a `page=0` call). 🟢.

### Transactions
- Status filter chips, search box, full table → `GET /api/v1/payments?status=&q=&page=&size=`. 🟢. Table's Customer/Method/Provider/Created columns now map onto `customer`/`paymentMethod`/`providerDisplayName`/`createdAt` on each `PaymentView` in the page content. 🟢.
- Row click → `GET /api/v1/payments/{id}`. 🟢 (unchanged from first pass).

### Transaction Detail
- Amount/ID/status/decline reason → `PaymentView`. 🟢.
- Customer, method (brand+last4), provider display name, created, idempotency key → all now present on `PaymentView`. 🟢 (was 🟡/🔴 in the first pass).
- Timeline → `GET /api/v1/payments/{id}/events`, returns step label (`description`), timestamp, and a status to derive done/pending/failed styling from. 🟢 (was 🔴).
- Refund button → `POST /api/v1/payments/{id}/refund`. 🟢 (unchanged).
- Gap carried forward: neither `GET /api/v1/payments/{id}` nor `.../events` is merchant-scoped — any merchant token can still pull any payment/timeline by ID, not just their own.

### Ledger (merchant)
- Balances → `GET /accounts/{merchantId}/{accountName}/balance`. 🟢 (was 🟠).
- Double-entry log for one transaction → `GET /ledger?transaction=`. 🟢 (was 🟠), though still scoped to one `transactionRef` at a time — a merchant-wide "all my transactions' entries" view still needs multiple calls or a new merchant-scoped feed (the new `GET /operator/ledger/entries` is platform-wide and `ROLE_OPERATOR`-only, so it doesn't cover this use case).
- "Request Payout" → `POST /payouts`, status via `GET /payouts/{id}`. 🟢 (was 🟠).

### Ledger Integrity (operator)
- Integrity banner → `GET /operator/ledger/integrity`. 🟢 (unchanged).
- Platform account cards → `GET /accounts/{accountName}/balance` per card. 🟢 (was 🟠).
- Platform-wide double-entry log → `GET /operator/ledger/entries`. 🟢 (was 🔴 — this endpoint didn't exist at all before).

### Provider Health (operator)
- Name/id/active toggle → `GET`/`PATCH /operator/providers/{id}/routing`. 🟢 (unchanged).
- Healthy/Degraded pill, success rate, avg. latency → now all on `ProviderConfig`. 🟢 (was 🟡).
- `priority` → still readable/patchable but not surfaced in the UI design. Unchanged observation from the first pass.

### Saga Events (operator)
- `GET /operator/saga-events` unchanged: `{workflowId, status, startTime, closeTime}`, no narrative text, keyed by `workflowId` not payment ID. Still 🟡 exactly as before — BE-04..08 didn't touch the orchestrator.

### Developers
- Webhook "Recent deliveries" (mixed success/failure) → `GET /webhooks/deliveries`, merchant-scoped. 🟢 (was 🔴 — no such endpoint existed).
- API keys panel → still 🔴, no concept anywhere in the backend.
- Webhook URL registration/display → still 🔴, no endpoint.

---

## Summary of remaining gaps (backend)

- No API-key management endpoint — the whole Developers "API keys" panel has nothing to call.
- No endpoint to register or display a merchant's own webhook URL.
- No "active disputes" concept anywhere.
- `GET /api/v1/payments/{id}` and `GET /api/v1/payments/{id}/events` are not merchant-scoped (any merchant token can read any payment/timeline by ID).
- `GET /webhooks/deliveries` requires `X-Merchant-Id`, so it 400s for an operator-role token — fine for the Developers page (merchant-only) but worth knowing if an operator-facing "all deliveries" view is ever wanted.
- `PaymentSummaryResponse.volumeDeltaPct` and `.avgAuthSeconds` are hardcoded constants, not derived from real history — looks real, isn't yet.
- CORS allowlist is a single hardcoded origin (`http://localhost:4200`).

## Summary of remaining gaps (frontend catching up to backend)

- `PaymentService.listPayments()` and `.getPaymentEvents()` still call mocks; both target endpoints are live. Pure wiring work, not blocked.
- `PaymentViewEnriched`/`PaymentsSummary`/`ProviderConfig` FE models have drifted from the real DTOs they were stubbed against (field renames, a unit mismatch on `avgAuthSeconds` vs `avgAuthTimeMs`, and health fields the FE still expects as a separate, non-existent endpoint). These need a model update pass, not just new HTTP calls.
- `LedgerService` and `OperatorService` are empty — every endpoint they'll need already exists server-side.
- `overview`, `ledger`, `sandbox`, `developers`, and all three `operator/*` routes are still `PlaceholderView` — expected at this stage of the build, noted here only to keep this doc from overstating what's actually live in the app today.
