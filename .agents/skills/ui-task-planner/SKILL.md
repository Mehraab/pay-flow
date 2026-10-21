# Skill: PayFlow UI State & Task Planning (Angular Architecture)

## Context & Inputs
- **Workspace Root**: `/home/gmhossen/Project/pay-flow`
- **UI Mockup**: `/home/gmhossen/Project/pay-flow/PayFlow Console.html`
- **API Catalog**: `/home/gmhossen/Project/pay-flow/specs/API_CATALOG.md`
- **Contract Map & Gap Analysis**: `/home/gmhossen/Project/pay-flow/specs/API_UI_MAP.md`
- **Target Tech Stack**: **Latest Angular** (Standalone Components, Angular Signals, Functional Guards, Functional Interceptors).

## Objectives
1. Create `specs/BACKEND_TASKS.md`: An inventory of backend tickets to close the gaps discovered in `specs/API_UI_MAP.md`.
2. Create `specs/FRONTEND_TASKS.md`: An Angular-native implementation plan using standalone components, Signals, and the **Service Adapter Pattern** (real Gateway calls where available, fallback stubs where gapped).

---

## 1. Backend Gap Checklist (`specs/BACKEND_TASKS.md`)
Extract all 🔴 and 🟠 items from `specs/API_UI_MAP.md` into actionable tickets:
- **BE-01: Gateway Route Configuration (`payflow-api-gateway`)**:
  - Add proxy routes in `application.yml` for `payflow-ledger-service` (`/accounts/**`, `/payouts/**`).
  - Add CORS filter configuration for Angular dev servers (`http://localhost:4200`).
- **BE-02: Payment List Query (`payflow-payment-query-service`)**:
  - Implement `GET /api/v1/payments` (supports pagination, filtering by `status`, `merchantId`).
- **BE-03: Enrich PaymentView DTO**:
  - Add `createdAt`, `customer`, `paymentMethod` (masked brand + last4) to `PaymentView`.
- **BE-04: Metrics / Summary Endpoint**:
  - Add `GET /api/v1/payments/summary` for 30-day net volume and success rates.

---

## 2. Angular Architecture & Service Adapter Pattern
The frontend will live in `frontend/` (Angular CLI standalone project):
1. **Reactivity with Angular Signals**:
   - Component state must use `signal()` and `computed()` (e.g., computing display dollar amount from `amountMinor` cents).
   - Use `computed()` for filtered transaction feeds and balance checks.
2. **HTTP Interceptor (`auth.interceptor.ts`)**:
   - Modern `HttpInterceptorFn`:
     - Attaches `Authorization: Bearer <token>` to all calls hitting `http://localhost:8080`.
     - On 401 error: intercepts response, invokes token refresh (`POST /auth/refresh`), updates the signal in `AuthService`, and replays the original request.
3. **Functional Route Guards (`auth.guard.ts`)**:
   - `canActivateFn` verifying `AuthService.role()`:
     - `merchantGuard`: requires `ROLE_MERCHANT`.
     - `operatorGuard`: requires `ROLE_OPERATOR`.
4. **Service Adapter Layer (`core/services/`)**:
   - Components NEVER call `HttpClient` directly.
   - All network calls live in injectable services (`PaymentService`, `LedgerService`, `OperatorService`, `AuthService`).
   - For real endpoints (`/auth/token`, `/operator/ledger/integrity`, `/api/v1/payments/{id}/refund`), call the real Gateway.
   - For gapped endpoints (`GET /api/v1/payments` list, volume metrics), return typed `Observable` of mock data with a `// TODO: Wire to backend once BE-XX is merged` flag.

---

## 3. UI State Matrix (Mandatory for Every Angular Component)
Every task in `specs/FRONTEND_TASKS.md` must explicitly define:
- **Loading State**: `isLoading = signal(true)` with skeleton loaders or disabled buttons.
- **Error State**: `errorMessage = signal<string | null>(null)` with toast or inline error banner.
- **Empty State**: Custom placeholder when arrays/lists have zero items (`items().length === 0`).
- **Success State**: Populated view styled to match `PayFlow Console.html`.

---

## 4. Chunked Angular Tasks Breakdown
Break the build into 7 sequential tasks:
- **Task 1: Angular Scaffolding & Core Infrastructure** (Angular standalone app, CSS variables from `PayFlow Console.html`, `AuthService` with Signals, `authInterceptor`, `authGuard`).
- **Task 2: App Shell, Navigation Rail & Role Switcher** (Sidebar rail, role switcher signal, theme toggle, header).
- **Task 3: Merchant Portal — Transactions Feed & Detail Modal** (`TransactionsComponent`, status filter chips, payment detail drawer with timeline & refund trigger `POST /api/v1/payments/{id}/refund`).
- **Task 4: Interactive Payment Sandbox & Saga Compensation Demo** (`CheckoutDemoComponent`, test card quick-fill buttons `4242`, `0002`, `0069`, `0119`, and "Force Ledger Failure" toggle sending `X-Force-Ledger-Failure: true` header to test Temporal Saga compensation to `REFUNDED`).
- **Task 5: Operator Console — Ledger Integrity & Saga Events** (`LedgerIntegrityComponent` calling real `GET /operator/ledger/integrity`, `SagaEventsComponent` calling `GET /operator/saga-events`).
- **Task 6: Operator Console — Provider Routing & Webhook DLQ** (`ProviderRoutingComponent`, `WebhookDlqComponent` with retry triggers).
- **Task 7: Overview Dashboard & Analytics** (`OverviewComponent`, volume metrics, SVG chart with adapter stub).

---

## Output Contract
Write:
1. `specs/BACKEND_TASKS.md`
2. `specs/FRONTEND_TASKS.md`
