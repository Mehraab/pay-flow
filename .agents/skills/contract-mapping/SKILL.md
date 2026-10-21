# Skill: PayFlow UI-to-API Contract Mapping & Gap Analysis

## Context & Source Files
- **Workspace Root**: `/home/gmhossen/Project/pay-flow`
- **UI Template**: `/home/gmhossen/Project/pay-flow/PayFlow Console.html`
- **Backend API Catalog**: `/home/gmhossen/Project/pay-flow/specs/API_CATALOG.md`
- **Architecture & System Contracts**: `/home/gmhossen/Project/pay-flow/ARCHITECTURE_AND_PLAN.md`
- **Output Artifact**: `/home/gmhossen/Project/pay-flow/specs/API_UI_MAP.md`

## Objective
Analyze `PayFlow Console.html` against `specs/API_CATALOG.md` and `ARCHITECTURE_AND_PLAN.md`. Map every view, table, button, toggle, and dynamic field to the single-entry Gateway APIs (`http://localhost:8080`) and identify all integration gaps.

## Concrete PayFlow Console Views to Map

Inspect each `<section class="view">` in `PayFlow Console.html`:

1. **Overview View (`#view-overview`)**:
   - Volume metrics & daily payment chart (October daily volume).
   - "Recent transactions" mini-feed (Lines 550–559).
   - Test vs Live mode toggle (Lines 540–544).

2. **Transactions View (`#view-transactions`)**:
   - Table columns: `id`, `customer`, `amount`, `status` (`captured`, `refunded`, `failed`), `method`, `provider`, `created`.
   - Row click interaction $\to$ activates `#view-detail`.
   - Filters (status, provider, date).

3. **Transaction Detail View (`#view-detail`)**:
   - **Timeline**: `steps` (`Payment created` $\to$ `Authorized` $\to$ `Captured` / `Refunded`).
   - **Details definition list**: Payment ID, Amount (display dollars $\leftrightarrow$ backend `amountMinor`), Customer, Method, Provider.
   - **Raw Event Payload**: `<details>` collapsible block showing `payment.updated` event payload.

4. **Ledger View (`#view-ledger`)**:
   - Double-entry bookkeeping log (`debit_account`, `credit_account`, `amount`, `status`).
   - Rebuilt from Kafka event log & `payflow-ledger-service`.

5. **Integrity View (`#view-integrity`)**:
   - Real-time balance integrity check (`Total Debits == Total Credits`).
   - Discrepancy counter & mismatch alert.

6. **Provider Health & Routing (`#view-provider-health`)**:
   - Provider A vs Provider B routing priorities.
   - Circuit breaker / health status toggles (BR-13).

7. **Saga Events View (`#view-saga-events`)**:
   - Temporal Saga execution stream: workflow IDs, run IDs, saga state transitions, compensation events.

8. **Developers View (`#view-developers`)**:
   - API Keys generation/listing.
   - Webhook endpoints & Webhook DLQ delivery attempts (BR-15).

9. **Interactive Checkout & Card Simulation**:
   - Mock card trigger buttons (`4242` for CAPTURED, `0002` for Insufficient Funds, `0069` for Expired, `0119` for Timeout).
   - "Force Ledger Failure" trigger (header `X-Force-Ledger-Failure: true` / body flag) $\to$ tests Temporal Saga compensation to `REFUNDED`.

## Domain & Unit Conversions
- **Currency**: `amountMinor` integer (cents) in backend $\leftrightarrow$ Formatted `$XX.XX` in HTML.
- **Status Mapping**:
  - `captured` $\leftrightarrow$ `CAPTURED`
  - `pending` $\leftrightarrow$ `CREATED` / `PENDING`
  - `failed` $\leftrightarrow$ `FAILED`
  - `refunded` $\leftrightarrow$ `REFUNDED`

## Gap Analysis (Mandatory Section)
Explicitly document:
1. **Unimplemented Backend Routes**: Features present in `PayFlow Console.html` that have no corresponding route in `specs/API_CATALOG.md`.
2. **Missing UI Inputs**: Required backend DTO parameters that do not exist in the HTML layout.
3. **CORS Requirement**: Highlight `payflow-api-gateway` configuration needed for `http://localhost:3000` / `5173`.
4. **Auth Scoping**: Gateway JWT filter requirements (`ROLE_MERCHANT` vs `ROLE_OPERATOR`).

## Output Contract
Write the output exclusively to `/home/gmhossen/Project/pay-flow/specs/API_UI_MAP.md`.
