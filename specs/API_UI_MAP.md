# PayFlow Console → Gateway API Map

Maps every data-bearing element and action trigger in `PayFlow Console.html` to the
Gateway routes documented in `specs/API_CATALOG.md`. The console today is a static
prototype — `TRANSACTIONS` is a hardcoded JS array, the sign-in form does `e.preventDefault()`
and nothing else, and every button mutates local state only. **No `fetch`/`XHR` call
exists anywhere in the file.** This doc maps what *would* wire to what, and calls out
where the Gateway has no matching route or the response schema is missing fields the UI
already renders.

**Architecture note**: PayFlow is the payment *processor*. The merchant's own website
owns checkout and calls `POST /api/v1/payments` server-to-server with the card data —
this console never does. The console is the merchant/operator dashboard: read-mostly
views over payments/ledger/platform-health, plus `POST
/api/v1/payments/{id}/refund` as its one write action. Keep that boundary in mind
below — "no payment-creation UI" is correct design, not a gap.

Severity key: 🔴 no backend route exists at all · 🟠 route exists but not gateway-routed
(per Catalog) · 🟡 route exists and is gateway-routed, but response schema is missing
fields the UI needs · 🟢 clean match.

---

## 0. Sign-in screen (`#signin`)

| UI element | Gateway API | Status |
|---|---|---|
| Email/password form, "Continue" | `POST /auth/token` | 🔴 Not called. Form submit just hides the overlay (`e.preventDefault()`); no `fetch` to `/auth/token`, no token stored, nothing sent. Email/password fields don't correspond to `TokenRequest` (`role`, `merchantId`) at all — the real auth model is roleless-credential, token-based with no password. |
| Role toggle (Merchant/Operator) in the rail | `POST /auth/token` (re-issue with `role: "MERCHANT"` / `"OPERATOR"`) | 🔴 `setRole()` only flips a `data-role` body attribute and re-renders. A real integration needs a fresh token per role (the JWT's `role` claim is what the gateway's `JwtAuthenticationFilter` checks for `/operator/**`), so this toggle should call `/auth/token` again, not just restyle the page. |
| Environment toggle (Test/Live) | *(none)* | 🔴 **Gap — no backend concept.** The Catalog has no test/live environment split anywhere (no separate base URL, no header, no field). `envTest`/`envLive` just set `data-env="live"` to swap in an empty-state illustration. If this is a real product requirement, it needs a new Gateway concept (e.g. a `X-Environment` header or separate test/live API-key scoping) that doesn't exist today. |
| "Design prototype — any credentials continue" note | — | Confirms the above is intentional for now; flagging since the task asks for gaps. |

---

## 1. Overview (`#view-overview`)

| UI element | Gateway API | Status |
|---|---|---|
| Stat: Net volume (30d), delta | *(none)* | 🔴 No aggregation/analytics endpoint exists anywhere in the Catalog. Would need something like `GET /api/v1/payments/summary` on the query service — not present. |
| Stat: Success rate | *(none)* | 🔴 Same — no aggregate endpoint. |
| Stat: Avg. authorization time | *(none)* | 🔴 Same, and no timing data is captured in `PaymentView` to derive it from either. |
| Stat: Active disputes | *(none)* | 🔴 Disputes aren't a modeled concept anywhere in the Catalog (no dispute endpoints, no `status` value for it in `PaymentView`). |
| "Volume, last 14 days" chart | *(none)* | 🔴 Same aggregation gap as net volume. |
| "Recent transactions" table (6 rows: id, customer, method, status, amount) | would be `GET /api/v1/payments` (list) | 🔴 **Gap — no list endpoint exists.** The Catalog documents only `POST /api/v1/payments` (create) and `GET /api/v1/payments/{id}` (single, by ID) on the payment-query path. There is no "list payments for a merchant" or "recent payments feed" route at all, gateway-routed or otherwise. Every list-shaped view in this console (Overview's recent list, the full Transactions table) has **no backing route today.** |

---

## 2. Transactions (`#view-transactions`)

| UI element | Gateway API | Status |
|---|---|---|
| Status filter chips (All/Captured/Pending/Failed/Refunded) | would be `GET /api/v1/payments?status=...` | 🔴 Same missing-list-endpoint gap as above; a status filter param has nothing to attach to. |
| Global search (`#globalSearch`, by ID or customer) | would be `GET /api/v1/payments?q=...` | 🔴 Same gap. Also: `PaymentView` has no `customer` field (see §3 below), so even a hypothetical list endpoint couldn't search by customer name without a schema change. |
| Table columns: ID, Customer, Method, Provider, Status, Created, Amount | — | 🟡/🔴 `ID`→`paymentId`, `Status`→`status`, `Amount`→`amountMinor`/`currency` would map onto `PaymentView` fields fine *if* a list endpoint existed. `Customer`, `Method` (card brand/last4), `Provider` (display name vs. `providerRef`), and `Created` (timestamp) are **not present in `PaymentView` at all** — see §3. |
| Row click → open detail | `GET /api/v1/payments/{id}` | 🟢 Clean match — this is the one place the console's data shape lines up with a real, gateway-routed endpoint. |

---

## 3. Transaction Detail (`#view-detail`)

| UI element | Gateway API | Status |
|---|---|---|
| `dAmount`, `dId` | `GET /api/v1/payments/{id}` → `amountMinor`+`currency`, `paymentId` | 🟢 |
| `dStatus` pill | `GET /api/v1/payments/{id}` → `status` | 🟢 (UI statuses `captured/pending/failed/refunded` vs. catalog's example values `PENDING/CAPTURED/DECLINED/REFUNDED` — casing/vocabulary should be confirmed to match exactly; UI currently hardcodes its own four lowercase values and will need a mapping table either way since "failed" ≠ "DECLINED" verbatim.) |
| `dCustomer` | *(none)* | 🟡 **Missing field.** `PaymentView` has no customer name/email. |
| `dMethod` (card brand + last4) | *(none)* | 🟡 **Missing field.** `PaymentView` has no card/payment-method info. The command-service *request* takes a raw `cardNumber`, but nothing in the response path echoes back a masked brand/last4. |
| `dProvider` | `providerRef` (loosely) | 🟡 `PaymentView.providerRef` is a provider reference string, not a display name like "Provider A"/"Provider B" — would need either a lookup against `GET /operator/providers` (operator-only!) or a new field. |
| `dCreated` | *(none)* | 🟡 **Missing field.** No `createdAt`/timestamp anywhere in `PaymentView`. |
| `dIdem` (idempotency key) | *(none)* | 🟡 **Missing field.** The `Idempotency-Key` header is accepted on `POST /api/v1/payments` but never echoed back by `GET /api/v1/payments/{id}`. |
| Timeline (`Payment created → Authorized → Captured → Ledger entry posted → Webhook delivered → Refund requested → Refunded`, with timestamps per step) | *(none)* | 🔴 **No endpoint returns a per-payment event timeline.** `/operator/saga-events` (operator-only) returns one row per Temporal workflow (`status`, `startTime`, `closeTime`) — not a step-by-step audit trail, and it's keyed by `workflowId`, not `paymentId`. Building this timeline for real needs either a new "payment events" endpoint or correlating `workflowId`↔`paymentId`, which the Catalog gives no mechanism for. |
| Decline reason note on failed step | `declineReason` | 🟢 Matches `PaymentView.declineReason`. |
| "Refund" button → `doRefund()` | `POST /api/v1/payments/{id}/refund` | 🟢 Clean match (`ROLE_MERCHANT`/`ROLE_OPERATOR`, no body). Note the UI's `confirm()` dialog is purely client-side; the real endpoint returns 200 with an empty body, so the UI's optimistic `t.status = "refunded"` will need to instead re-poll `GET /api/v1/payments/{id}` since the command is fire-and-forget (saga-driven, async). |
| "Raw event" JSON panel (`payment.updated` payload: `object`, `payment_method`, `idempotency_key`, `created`) | *(none)* | 🔴 This is a Stripe-style webhook-payload mock; no such webhook event schema is defined anywhere in the Catalog (`/internal/webhooks/dispatch` only takes an opaque `payload` map — the shape isn't specified). |

---

## 4. Ledger (merchant view, `#view-ledger`)

| UI element | Gateway API | Status |
|---|---|---|
| "Merchant payable (available)" balance | `GET /accounts/{merchantId}/{accountName}/balance` | 🟠 **Not gateway-routed.** Route exists on `payflow-ledger-service` but the Catalog explicitly lists it under "not reachable via `localhost:8080` today." |
| "Payout in-transit" balance | same route, different `accountName` | 🟠 Same gap. |
| Double-entry log table (Date, Transaction, Debit account, Credit account, Status, Amount) | `GET /ledger?transaction={transactionRef}` | 🟠🔴 Doubly gapped: (1) not gateway-routed per Catalog, and (2) even if it were, that route returns entries for **one** `transactionRef` at a time — there is no "all ledger entries for this merchant across all transactions" endpoint, which is what this table actually needs to render more than one row. Also `LedgerEntryResponse` has no `status` field ("Posted") the table displays. |
| "Request Payout" button → `requestPayout()` | `POST /payouts` | 🟠 **Not gateway-routed.** Also the UI's optimistic transition (In-Transit → Settled after a 2.4s `setTimeout`) is fully fake; a real integration would need to poll `GET /payouts/{id}` for status (also not gateway-routed) rather than assume settlement. |

**Section-wide gap**: every ledger/payout API this view needs exists on `payflow-ledger-service` but **none of them are reachable through the Gateway today** (per Catalog §3). This entire view is non-functional against `localhost:8080` as things stand, independent of the UI still being static-mocked.

---

## 5. Ledger Integrity (operator, `#view-integrity`)

| UI element | Gateway API | Status |
|---|---|---|
| Integrity banner ("Balanced — total debits − total credits = $0.00") | `GET /operator/ledger/integrity` → `{totalDebitsMinor, totalCreditsMinor, balanced}` | 🟢 Clean, gateway-routed, `ROLE_OPERATOR`. (UI note: the banner should compute `totalDebitsMinor - totalCreditsMinor` and the ✓/✗ icon from `balanced`, rather than hardcoding "$0.00" and a green check — that's a UI wiring task, not an API gap.) |
| Platform account cards (Provider settlement, Platform bank, Platform fee revenue, Merchant payable all-merchants, Payout in-transit all-merchants) | `GET /accounts/{accountName}/balance` per card | 🟠 Same not-gateway-routed gap as §4, repeated per platform account name. |
| "Platform double-entry log" table (across all merchants) | *(none)* | 🔴 **Gap — no endpoint exists for this at all.** `GET /ledger?transaction=X` is scoped to one transaction; there is no platform-wide ledger feed in the Catalog, gateway-routed or not. This is a bigger gap than §4's — it's missing from the ledger service's surface entirely, not just missing from the gateway. |

---

## 6. Provider Health (operator, `#view-provider-health`)

| UI element | Gateway API | Status |
|---|---|---|
| Provider name, id | `GET /operator/providers` → `ProviderConfig[].name`/`.id` | 🟢 |
| Active/paused toggle | `PATCH /operator/providers/{id}/routing` → `{active}` | 🟢 Clean match, `ROLE_OPERATOR`. |
| "Healthy"/"Degraded" status pill | *(none)* | 🟡 **Missing field.** `ProviderConfig` has no health-status field (`{id, name, priority, active}` only). |
| Success rate, avg. latency stats | *(none)* | 🟡 **Missing fields.** Not present on `ProviderConfig`; would need new fields or a separate metrics endpoint. |
| `priority` field | `ProviderConfig.priority` | 🟡 **Unused in UI.** Backend has it (and `PATCH .../routing` accepts updating it), but the console never displays or edits priority — only the active toggle is wired. Worth flagging as a UI gap in the other direction: a capability the backend exposes that the console doesn't surface. |
| Routing note text at bottom | derived client-side from the two toggles | 🟢 No API needed — purely reflects local toggle state. |

---

## 7. Saga Events (operator, `#view-saga-events`)

| UI element | Gateway API | Status |
|---|---|---|
| Table: Time, Transaction, Event, Status | `GET /operator/saga-events` → `SagaEventResponse[] {workflowId, status, startTime, closeTime}` | 🟡 Route is gateway-routed and `ROLE_OPERATOR`-gated — the closest real match of any operator view — but the shapes diverge: UI's "Transaction" column shows a `pi_...` payment ID while the response key is `workflowId` (Catalog doesn't document a guaranteed 1:1 naming convention between the two); UI's "Event" column is a free-text narrative ("Ledger write failed → compensating refund issued (6b)") that doesn't exist in `SagaEventResponse` — only `status`/`startTime`/`closeTime` do; and the UI implies a feed of *multiple* discrete events per saga, while the endpoint gives one row per workflow execution (start/close), not a step-by-step compensation log. |

---

## 8. Developers (merchant, `#view-developers`)

| UI element | Gateway API | Status |
|---|---|---|
| API keys list (Test/Live publishable + secret keys, created/last-used, copy) | *(none)* | 🔴 **Gap — no API-key concept exists anywhere in the Catalog.** Auth is JWT bearer-token based (`POST /auth/token`/`/auth/refresh`/`/auth/revoke`), issued per role, not per-merchant long-lived key pairs. This entire panel models a Stripe-style key system the backend doesn't have. |
| Webhook endpoint URL + "Copy URL" | *(none)* | 🔴 No endpoint to register/view a merchant's own webhook URL. `/internal/webhooks/dispatch` is an orchestrator-internal enqueue call, not merchant-facing config. |
| "Recent deliveries" table (event, status code, attempt, delivered time) | `GET /operator/webhooks/dlq` (closest match) | 🔴🟡 Doubly wrong fit: (1) this Developers view is under `role-merchant-only` nav, but the only webhook-delivery-listing route in the Catalog, `GET /operator/webhooks/dlq`, requires `ROLE_OPERATOR` — a merchant token would get a 403 even if it were wired up; and (2) that route only returns **dead-lettered** (failed/retrying) deliveries (`WebhookDeliveryResponse[]` with `attemptCount`, `responseCode`, `nextRetryAt`), whereas the UI table shows a mix including clean `200` first-attempt deliveries that would never appear in a DLQ. There is no "all webhook deliveries, successful and failed, for my merchant account" endpoint. |

---

## Card simulator — not a console concern

**No card-simulator UI exists in this console, and it shouldn't.** `POST
/api/v1/payments` (the endpoint the Catalog's Conventions section documents the
test-card routing for — `4242`→approved, `0002`/`0069`→declined, `0119`→timeout — plus
the `X-Force-Ledger-Failure`/`forceLedgerFailure` saga-compensation trigger) is called
**server-to-server by the merchant's own website/backend**, not by this console. The
merchant (Northline Goods, in this prototype) collects the card on their own checkout
page and their backend calls PayFlow's Payment API directly with the `Idempotency-Key`
header and `cardNumber` in the body — PayFlow is the processor here, the console is
just the merchant/operator's dashboard onto payments that already exist. Card numbers
in the console only ever appear as masked display strings in the (mocked) transaction
list (e.g. `"Visa •••• 4242"`); that's correct — the console should never see a raw
card number at all. Initially corrected in this doc's first pass, which wrongly flagged
this as a gap.

This reframes who integrates with what:
- **Merchant's website/backend** → `POST /api/v1/payments` (create), reading back
  `GET /api/v1/payments/{id}` to poll status, and receiving webhook callbacks.
- **This console** → read-mostly views over payments, ledger, and (for operators)
  platform health — plus the one write action it does own, `POST
  /api/v1/payments/{id}/refund`.

---

## Summary of gaps

### No backend route exists at all (🔴)
- A bulk/list payments endpoint (`GET /api/v1/payments` with filter/search) — needed by
  Overview's recent list and the entire Transactions view.
- Any analytics/aggregation endpoint (net volume, success rate, avg. auth time, disputes,
  the 14-day volume chart).
- A per-payment event/audit timeline endpoint (Transaction Detail's timeline).
- A platform-wide (all-merchants) ledger entry feed (Ledger Integrity's double-entry log).
- An API-key management surface (Developers' key list) — the backend's auth model has no
  such concept.
- A merchant-facing webhook-URL registration/config endpoint.
- A test/live environment concept of any kind.

Note: `POST /api/v1/payments` is deliberately *not* called from the console — it's
called server-to-server from the merchant's own website/backend, which owns the
checkout/card-collection UI. The console only consumes its effects (via the list/detail
endpoints above, which are the actual gap).

### Route exists, but not reachable via the Gateway (🟠, per Catalog §3)
- `GET /accounts/{accountName}/balance`, `GET /accounts/{merchantId}/{accountName}/balance`
  — needed by both Ledger views' balance cards.
- `GET /ledger?transaction=...` — needed by both ledger tables (and even if routed, it's
  scoped to one transaction, not a merchant- or platform-wide feed).
- `POST /payouts`, `GET /payouts/{id}`, `POST /payouts/sweep` — needed by "Request Payout."

### Route exists and is gateway-routed, but response schema is missing UI fields (🟡)
- `PaymentView` (`GET /api/v1/payments/{id}`) has no `customer`, payment-method/card
  display, `createdAt`, idempotency key, or provider display name — all rendered in the
  Detail view today from mock data only.
- `ProviderConfig` (`GET /operator/providers`) has no health-status, success-rate, or
  latency fields — all shown in Provider Health from mock data only.
- `SagaEventResponse` (`GET /operator/saga-events`) has no narrative event description,
  and is keyed by `workflowId` rather than payment/transaction ID.
- `GET /operator/webhooks/dlq` only covers dead-lettered deliveries and is
  `ROLE_OPERATOR`-only, while the Developers view (merchant-facing) shows a mixed
  success/failure delivery log.

### Backend capability unused by the UI
- `ProviderConfig.priority` is readable and patchable via
  `PATCH /operator/providers/{id}/routing`, but Provider Health never displays or edits it.

### Role/auth model mismatch
- Sign-in and the Merchant/Operator rail toggle never call `POST /auth/token`; there is
  no token acquisition, storage, or re-issuance on role switch anywhere in the console,
  so none of the `Authorization: Bearer` / `ROLE_OPERATOR` gating the Catalog documents
  is actually exercised by this prototype yet.
