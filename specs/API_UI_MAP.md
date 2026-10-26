# PayFlow Console → Gateway API Map

**Third pass — live-verified.** FE and BE are both reported done and all services are
running (`:8080` gateway, `:8081-8086` downstream, `:4200` Angular dev server). This
pass doesn't just re-read source: it hits the running gateway directly with `curl` and
drives the actual Angular app through the browser for every view, signed in as both
roles. Everything below marked ✅ was observed working live, not inferred from code.

**Architecture note (unchanged, now confirmed in-product):** PayFlow is the payment
*processor*. The merchant's own website calls `POST /api/v1/payments` server-to-server
— this console never does, except as an internal dev tool. The Sandbox page now says so
verbatim in its own banner: *"Internal engineering tool — not the real checkout path.
In production, `POST /api/v1/payments` is called server-to-server by the merchant's own
site, never by this console."*

---

## 1. The real finding this pass: two confirmed data-exposure gaps

Both reproduced live with curl, not just read from source.

### 🔴 Payment detail/timeline are not merchant-scoped (cross-merchant data leak)
```
Merchant A creates a payment (amountMinor 5000, customer "Live Review Test").
Merchant B (a different, freshly-issued merchantId) then calls, with ONLY its own token:
  GET /api/v1/payments/{A's paymentId}        → 200, full PaymentView (customer name,
                                                  idempotency key, provider ref, all of it)
  GET /api/v1/payments/{A's paymentId}/events  → 200, full timeline
  GET /api/v1/payments?page=0&size=5           → 200, content: [] (correctly scoped)
```
`findPayments`/`getSummary` filter by `X-Merchant-Id`; `findById`/`findTimeline` do not
(`PaymentViewStore.java` — confirmed by reading the methods: `findById` and
`findTimeline` take only the UUID, no merchant check). Any authenticated merchant can
read any other merchant's payment detail and full event history by ID — they just can't
discover the ID through search. This is the single most actionable finding in this
report; it was flagged as a lower-severity note in the previous pass and deserves to be
called out as the top item now that it's reproduced.

### 🟡 Platform account balances have no role gate
```
curl .../accounts/Platform%20Bank/balance  -H "Authorization: Bearer <MERCHANT token>"
→ 200 {"accountName":"Platform Bank","balanceMinor":50905}
```
`GET /accounts/{accountName}/balance` sits outside `/operator/**`, so the gateway's
`ROLE_OPERATOR` gate never applies to it — any merchant token can read the platform's
total bank balance, provider settlement balance, and fee revenue (the same three
figures Ledger Integrity shows only to operators in the UI). The UI never exposes this
to a merchant, but the API does. Lower severity than the first item (no per-merchant
customer data), but still a platform-financials disclosure across the merchant/operator
boundary that's worth a conscious decision rather than an accident of routing.

---

## 2. Frontend/backend contract — now matches, confirmed

Everything flagged as "stale TODO" or "model drift" in the previous pass is fixed, and
I re-verified both the code and the live UI:

| Area | Status |
|---|---|
| `PaymentService.listPayments()` / `.getPaymentEvents()` | ✅ Wired to real endpoints (`payment.service.ts`); `payment-api.mapper.ts` now handles the `customer`→`customerName`, `paymentMethod{brand,last4}`→`method` string reshaping explicitly. |
| `PaymentsSummary` model | ✅ Matches `PaymentSummaryResponse` exactly, including the `avgAuthSeconds` field name (the old `avgAuthTimeMs` unit-mismatch stub is gone) and the `currency`/`volumeDeltaPct` fields that were previously missing. |
| `ProviderConfig` model | ✅ `healthStatus`/`successRatePct`/`avgLatencyMs` folded directly into the one model, matching BE-07's actual shape; the old separate, never-built `ProviderHealth` stub type is gone. |
| `LedgerService` / `OperatorService` | ✅ Implemented: `getLedgerIntegrity`, `getPlatformAccountCards`, `getLedgerEntries`, `listSagaEvents`, `listProviders`, `updateProviderRouting`, `listWebhookDlq`, `retryWebhookDelivery` — all hitting real routes. |
| Sandbox / card simulator | ✅ Now exists (`features/sandbox/checkout-demo`), wasn't there in pass 1. Presets for all four documented test cards (4242/0002/0069/0119), a "Force ledger failure" checkbox wired to the `X-Force-Ledger-Failure` header (not the body flag — correctly matches the Catalog's convention), and polls `GET /api/v1/payments/{id}` for a terminal status after submit. Live-tested end to end: submitted a $214.00 4242 charge, got back `CAPTURED` with a real `providerRef`, and it showed up correctly in Transactions and the Detail view immediately after. |
| Overview's "Active disputes" stat | Resolved by removal, not by wiring — the FE simply doesn't render a 4th stat card for it, rather than faking a number against a concept the backend never modeled. Confirmed in the live screenshot: only 3 stat cards. |
| Saga Events table | Correctly scoped down from the original design mock's wishful narrative "Event" column — now shows `Workflow ID` (honest about the real key) and a derived `Duration`, nothing invented. |
| Detail view's raw-event panel | Still explicitly honest about its own gap: the UI's own copy reads *"payment.updated payload (mocked — no real webhook schema yet)"* — i.e. the frontend is self-documenting that this field has no real backend contract, rather than silently faking one. |

---

## 3. View-by-view, live-confirmed

| View | Route | Status |
|---|---|---|
| Overview | `overview` | ✅ Built, live-tested both roles. Merchant sees its own scoped summary/recent list; operator sees the platform-wide aggregate (verified: creating a payment as a merchant immediately changed the operator's "Net volume" and "Recent transactions"). |
| Transactions | `transactions` | ✅ Built, live-tested. |
| Transaction Detail | `transactions/:id` | ✅ Built, live-tested (amount, status, refund button, full timeline, details, raw-event panel all render from real data). |
| Ledger (merchant) | `ledger` | 🔴 Still `PlaceholderView` — *"This view hasn't been built yet — check back once its task lands."* Confirmed live. The backing routes (`GET /accounts/{merchantId}/{accountName}/balance`, `GET /ledger?transaction=`, `POST /payouts`, `GET /payouts/{id}`) all exist and work (verified `GET /accounts/{merchantId}/Merchant Payable/balance` → `200`), but `LedgerService` has no methods for them yet (only the operator-facing ones are implemented) and no component consumes them. |
| Sandbox (Demo) | `sandbox` | ✅ Built and live-tested end to end (see §2). |
| Developers | `developers` | 🔴 Still `PlaceholderView`, confirmed live. `GET /webhooks/deliveries` (merchant-scoped delivery log, live-tested, returns `200 {"content":[],...}` for this merchant) has no FE method or component yet. API keys remain entirely unbacked on the server (unchanged from prior passes). |
| Ledger Integrity | `operator/integrity` | ✅ Built, live-tested. 3 of 5 account cards are real (`Provider Settlement`, `Platform Bank`, `Platform Fee Revenue`, all fetched live); the other 2 (`Merchant payable (all-merchants)`, `Payout in-transit (all-merchants)`) are explicitly labeled **"mocked"** in the UI itself, because no all-merchants aggregate exists for those two liability accounts — this is an honest, documented gap, not a hidden one. Platform double-entry log is the real paginated feed. |
| Provider Health | `operator/providers` | ✅ Built, live-tested, matches `GET /operator/providers` exactly including `priority` (now surfaced, closing the one "unused backend capability" note from pass 1). |
| Saga Events | `operator/saga-events` | ✅ Built, live-tested, correctly scoped to the real `{workflowId, status, startTime, closeTime→duration}` shape. |
| Webhook DLQ | `operator/webhooks/dlq` | ✅ Built, live-tested (empty state renders correctly — *"Nothing is stuck — every webhook has been delivered successfully."*). |

---

## 4. Minor frontend bug found live

`AuthService.applyTokenResponse()` (`core/services/auth.service.ts`) sets the local
`merchantId` signal from the **parameter passed into `login()`**, not from the issued
JWT's own `merchantId` claim. Signing in as Merchant with the "Merchant ID" field left
blank — the documented, supported way to get one auto-generated — produces a perfectly
valid, correctly-scoped access token (confirmed: its `merchantId` claim is a real UUID,
and every API call through it is properly scoped), but the app's own `AuthService.
merchantId()` reads back `null`. Observed consequences: the rail footer shows "No
merchant scope" even though the session is scoped, and the Sandbox page's "Merchant ID"
field (which defaults from `authService.merchantId()`) comes up empty, forcing the user
to manually paste in a UUID they'd otherwise have no reason to know. Not a security
issue (the gateway and backend read the real claim correctly) — a cosmetic/UX gap where
the client doesn't decode its own token. Fix is local to `auth.service.ts`: decode the
JWT (or just always pass the merchantId back from the token response) instead of
trusting the caller-supplied parameter.

---

## 5. Remaining gaps, consolidated

**Backend:**
- `GET /api/v1/payments/{id}` and `.../events` are not merchant-scoped — confirmed live cross-merchant read (§1, top finding).
- `GET /accounts/{accountName}/balance` has no role gate — confirmed live merchant access to platform-wide figures (§1).
- No API-key management endpoint.
- No merchant webhook-URL registration endpoint.
- No "active disputes" concept (frontend now correctly omits rather than fakes this).
- No real webhook-payload schema for the Detail view's raw-event panel (frontend labels it mocked).
- No all-merchants aggregate for `Merchant Payable`/`Payout In-Transit` (frontend labels these two cards mocked rather than hiding the gap).
- `PaymentSummaryResponse.volumeDeltaPct`/`.avgAuthSeconds` are still hardcoded constants, not derived.
- CORS allowlist is a single hardcoded origin (`http://localhost:4200`).

**Frontend:**
- `ledger` (merchant) and `developers` routes are still `PlaceholderView`; their backing APIs all exist and work.
- `AuthService` doesn't decode its own JWT's `merchantId` claim, producing a stale/null local signal when a merchant signs in without specifying one (§4).
