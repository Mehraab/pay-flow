# PayFlow Console — Angular Implementation Plan

Target stack: Angular (standalone components, no `NgModule`), Signals for all component
and service state, `HttpInterceptorFn`, functional `CanActivateFn` guards. Source of
truth for visual fidelity is [`PayFlow Console.html`](../PayFlow%20Console.html) (DOM
ids, CSS custom properties); source of truth for what's real vs. mocked is
[`API_UI_MAP.md`](./API_UI_MAP.md) and the backlog in [`BACKEND_TASKS.md`](./BACKEND_TASKS.md).

No code is written as part of this document — it is the task breakdown only.

---

## Conventions used throughout this plan

### Service Adapter Pattern
Components never inject `HttpClient`. Every network call goes through one of exactly
four injectable services in `core/services/`: `AuthService`, `PaymentService`,
`LedgerService`, `OperatorService`. Each method on these services is tagged:

- **REAL** — calls the Gateway at `environment.apiBase` (`http://localhost:8080`)
  today, per `API_CATALOG.md`.
- **STUB(BE-xx)** — returns a typed `Observable` built from local mock fixtures
  (`of(mock).pipe(delay(300))`), annotated `// TODO: Wire to backend once BE-xx is
  merged`. The method signature already matches the real contract documented in
  `BACKEND_TASKS.md` so swapping the body for an `HttpClient` call later is a one-line
  change with no caller-side impact.

### UI state model (every view, no exceptions)
A shared discriminated shape in `shared/state/view-state.ts`:

```ts
export interface ViewState<T> {
  loading: boolean;
  error: string | null;
  data: T | null;
}
```

Every feature's facade/service exposes a single `signal<ViewState<T>>(...)`, and every
smart component derives four `computed()`s from it: `isLoading`, `hasError`,
`isEmpty` (`!loading && !error && (data is null or an empty array/list)`), and
`isSuccess` (`!loading && !error && data has content`). Templates switch on these with
`@if`/`@else if` — never raw `data() === null` checks sprinkled through markup. This is
the one shared abstraction introduced across the whole app; it is intentionally thin
(a type + four computeds per component) so it doesn't become a framework-on-top-of-a-
framework.

### Routing shape
One `Routes` array in `app.routes.ts`. Role-gated children under a layout route:

```
/signin                        (public)
/                               (AppShellComponent, canActivate: [authGuard])
  /overview                     (merchantGuard | operatorGuard — both roles see it)
  /transactions                 (merchantGuard)
  /transactions/:id              (merchantGuard)   -- detail is a routed view, not a modal,
                                                        to support deep-linking/back button,
                                                        matching the HTML's own back-link pattern
  /ledger                        (merchantGuard)   -- Task 8
  /sandbox                       (merchantGuard)   -- Task 4, see caveat there
  /developers                    (merchantGuard)   -- Task 8
  /operator/integrity            (operatorGuard)
  /operator/providers            (operatorGuard)
  /operator/saga-events           (operatorGuard)
```

---

## Task 1 — Angular Scaffolding & Core Infrastructure

**Goal**: everything downstream depends on this; no feature UI yet.

- `ng new payflow-console --standalone --routing --style=css` under `frontend/`.
- Port the CSS custom properties verbatim from `PayFlow Console.html` (`:root` block —
  `--bg`, `--surface`, `--ink`, `--accent`, `--status-captured/pending/failed/refunded`,
  the `--rail-*` tokens, plus the `prefers-color-scheme: dark` override block) into
  `src/styles.css` as the design-token baseline. Do not redesign colors — this is a
  port, not a restyle.
- `environment.ts` / `environment.development.ts`: `apiBase: 'http://localhost:8080'`.

### `core/models/` — domain interfaces
Typed 1:1 against `API_CATALOG.md` response bodies: `TokenResponse`, `PaymentView`
(plus a separate `PaymentViewEnriched` extending it with the BE-03 fields, used only by
STUB data until BE-03 ships), `LedgerEntryResponse`, `AccountBalanceResponse`,
`PayoutResponse`, `LedgerIntegrityResponse`, `ProviderConfig`, `SagaEventResponse`,
`WebhookDeliveryResponse`.

### `core/services/auth.service.ts` — `AuthService`
**Signal state model**:
```ts
interface AuthState {
  accessToken: string | null;
  refreshToken: string | null;
  role: 'MERCHANT' | 'OPERATOR' | null;
  merchantId: string | null;
  expiresAt: number | null;   // epoch ms, derived from expiresIn on login/refresh
}
private _state = signal<AuthState>(emptyAuthState());
readonly role = computed(() => this._state().role);
readonly merchantId = computed(() => this._state().merchantId);
readonly isAuthenticated = computed(() => this._state().accessToken !== null);
readonly accessToken = computed(() => this._state().accessToken);
```
Persisted to `sessionStorage` (not `localStorage` — access tokens are short-lived
15 min per the Catalog, no need to survive a browser restart) on every state change via
an `effect()`, and rehydrated in the constructor.

- `login(role, merchantId?)` — **REAL** `POST /auth/token`.
- `refreshAccessToken()` — **REAL** `POST /auth/refresh`. Internally wrapped in
  `shareReplay({ bufferSize: 1, refCount: true })` and memoized on a private field that
  is cleared in `finalize()`, so concurrent 401s from multiple simultaneous requests
  (see Task 1's interceptor) all await the *same* in-flight refresh call rather than
  firing `/auth/refresh` multiple times in a race.
- `revokeAndLogout()` — **REAL** `POST /auth/revoke`, then clears `_state` and
  `sessionStorage`, navigates to `/signin`.
- `switchRole(role, merchantId?)` — re-runs `login()` with the new role (per the map's
  §0 finding: the HTML's `setRole()` today only flips a CSS attribute; the real
  integration must re-issue a token since the JWT's `role` claim is what the gateway's
  `JwtAuthenticationFilter` checks for `/operator/**`).

### `core/interceptors/auth.interceptor.ts` — `authInterceptor: HttpInterceptorFn`
Refresh flow, in order:
1. If `req.url` starts with `environment.apiBase` and `authService.accessToken()` is
   set, clone the request with `Authorization: Bearer <token>`.
2. `next(req).pipe(catchError(...))`: on `HttpErrorResponse` with `status === 401`
   **and** the request wasn't already a retry (guard with a context token
   `IS_RETRY` set via `req.context` to avoid infinite loops on a bad refresh token):
   - Call `authService.refreshAccessToken()`.
   - On success: clone the *original* request with the new access token, mark it
     `IS_RETRY`, and re-dispatch via `next()`.
   - On failure (refresh token itself invalid/expired, Catalog's documented 401 case):
     call `authService.revokeAndLogout()` and re-throw so the caller's own error state
     still fires (satisfies the mandatory Error UI state rather than swallowing it).
3. Any other error status passes through unchanged for the calling service/component to
   surface.
- Registered via `provideHttpClient(withInterceptors([authInterceptor]))` in
  `app.config.ts`.

### `core/guards/auth.guard.ts` — functional guards
```ts
export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  if (auth.isAuthenticated()) return true;
  return inject(Router).createUrlTree(['/signin']);
};

export const merchantGuard: CanActivateFn = () =>
  inject(AuthService).role() === 'MERCHANT' || inject(Router).createUrlTree(['/signin']);

export const operatorGuard: CanActivateFn = () =>
  inject(AuthService).role() === 'OPERATOR' || inject(Router).createUrlTree(['/signin']);
```
Both role guards additionally imply `authGuard` (no token → `role()` is `null` → falls
through to the redirect), so they don't need to be chained separately in the route
config.

### Stub skeletons for `PaymentService`, `LedgerService`, `OperatorService`
Created empty (constructor + injected `HttpClient` + `environment`) in this task;
methods filled in task-by-task below as each feature needs them, so each service grows
with the feature that first consumes it rather than being fully speced up front against
unused methods.

**4 UI states**: N/A for this task — no components render yet.

---

## Task 2 — App Shell, Navigation Rail & Role Switcher

**Component**: `AppShellComponent` (`layout/app-shell/`) — the routed parent for every
authenticated view; hosts `<router-outlet>`.

Children, matching the HTML's rail 1:1:
- `NavRailComponent` — renders nav items from a static `NAV_ITEMS` config array
  (`{ label, icon, routerLink, view }`), filtered by `authService.role()` so Merchant
  users see Overview/Transactions/Ledger/Sandbox/Developers and Operator users see
  Overview/Ledger Integrity/Provider Health/Saga Events — mirroring the HTML's
  `data-role` body-attribute nav filtering, but driven by the real `role()` signal
  instead of a CSS toggle.
- `RoleSwitcherComponent` — the Merchant/Operator buttons (`#roleMerchant`/
  `#roleOperator`). On click, calls `AuthService.switchRole(...)` (a real re-login, per
  Task 1) and navigates to `/overview`. **Env toggle (Test/Live) is explicitly NOT
  implemented** — per the map's §0 finding this has no backend concept at all; render
  it as a disabled/tooltip'd control ("Coming soon") rather than silently dropping it,
  so the design intent isn't lost, but don't wire fake behavior to it.
- `HeaderBarComponent` — page title (derived from `ActivatedRoute` data, replacing the
  HTML's manual `pageTitle`/`pageSub` DOM writes) + the global search input. Search is
  wired to `TransactionsComponent`'s filter signal (Task 3) via a shared
  `SearchQueryService` holding one `signal<string>('')`, not a direct component
  reference — only active (non-disabled) on the Transactions route.

**Signal state**: none owned by the shell itself beyond what `AuthService` /
`SearchQueryService` already expose; the shell is a pure layout composer.

**4 UI states**: N/A — layout/chrome only, no async data of its own. (The shell does
render `authService.isAuthenticated()`-driven redirect via the guard, not a loading
spinner.)

---

## Task 3 — Merchant Portal: Transactions Feed & Detail View

### `PaymentService` additions
- `listPayments(filter: { status?: string; q?: string; page?: number })` —
  **STUB(BE-02)**. Mock fixture: 12–15 `PaymentViewEnriched` rows covering all four
  statuses so every filter chip has at least one match in dev.
- `getPayment(id: string)` — **REAL** `GET /api/v1/payments/{id}` (map's one clean
  🟢 match).
- `refundPayment(id: string)` — **REAL** `POST /api/v1/payments/{id}/refund`. Per the
  map's explicit note: the real endpoint is fire-and-forget (200, empty body, saga-
  driven async compensation) — **do not** optimistically set `status = 'refunded'`
  locally. Instead, after the call resolves, re-poll `getPayment(id)` on an interval
  (e.g. every 2s, up to 5 attempts) until `status` changes from its pre-refund value,
  then stop. Surface "Refund requested — updating…" as an interim UI state distinct
  from the four standard ones (a transient banner, not a new ViewState variant).
- `getPaymentEvents(id: string)` — **STUB(BE-05)**. Mock a 5–7 step timeline matching
  the HTML's narrative (`Payment created → Authorized → Captured → Ledger entry posted
  → Webhook delivered → [Refund requested → Refunded if applicable]`) with synthetic
  timestamps.

### `TransactionsComponent` (`features/transactions/transactions.component.ts`)
**Signal state**:
```ts
private state = signal<ViewState<PaymentViewEnriched[]>>({ loading: true, error: null, data: null });
statusFilter = signal<'all' | 'captured' | 'pending' | 'failed' | 'refunded'>('all');
searchQuery = this.searchQueryService.query; // injected, shared with header
filtered = computed(() => applyFilters(this.state().data ?? [], this.statusFilter(), this.searchQuery()));
```
Filtering/search happen client-side over the STUB'd full result set for now (the mock
has no server-side `status`/`q` support to call); once BE-02 ships, move
`statusFilter`/`searchQuery` into the `listPayments()` call params instead of a local
`computed()` — noted as a follow-up, not built both ways now.

- **Loading**: skeleton rows (6 placeholder `<tr>`s, matching the HTML's existing table
  structure) while `state().loading`.
- **Error**: inline banner above the table ("Couldn't load transactions — retry") with
  a retry button that re-invokes `listPayments()`.
- **Empty**: shown when `filtered().length === 0` — distinguishes "no transactions at
  all" vs. "no matches for this filter/search" with different copy (check
  `statusFilter() === 'all' && !searchQuery()` to tell them apart).
- **Success**: the table, status-filter chip bar, and row click → `routerLink` to
  `/transactions/:id` (not a modal — see routing note in Conventions).

### `TransactionDetailComponent` (`features/transactions/transaction-detail/`)
**Signal state**:
```ts
private state = signal<ViewState<PaymentViewEnriched>>({ loading: true, error: null, data: null });
private timelineState = signal<ViewState<PaymentEvent[]>>({ loading: true, error: null, data: null });
isRefunding = signal(false);
refundStatusMessage = signal<string | null>(null); // the interim "updating…" banner above
```
Two independent `ViewState`s because the payment detail and its timeline are separate
STUB/REAL calls that shouldn't block each other's loading skeleton.

- **Loading**: skeleton for the amount/status header + timeline placeholders.
- **Error**: banner; the detail amount/id header (`dAmount`/`dId`/`dStatus`) and the
  timeline each get their own inline error affordance since one can fail independently
  of the other.
- **Empty**: N/A for the payment record itself (a 404 from `getPayment` is an Error
  state, "payment not found," not an Empty one); the timeline *can* legitimately be
  empty pre-BE-05 (stub returns `[]` for an edge-case fixture) — render "No events
  recorded yet."
- **Success**: amount/id/status pill, customer/method/provider/created/idempotency-key
  definition list, timeline, raw-event JSON panel (kept as a dev-only debug aid, mocked
  — per the map, no real webhook-payload schema exists to source it from), and the
  Refund button (`routerLink`'d `canDeactivate` not needed since refund doesn't
  navigate away).

---

## Task 4 — Interactive Payment Sandbox & Saga Compensation Demo

**⚠️ Architecture caveat, flag before building**: `API_UI_MAP.md`'s "Card simulator —
not a console concern" section is explicit that `POST /api/v1/payments` is called
server-to-server by the *merchant's own site*, never by this console, and that this is
correct design, not a gap. This task asks for exactly that call, from the console. Build
it, but as a clearly-labeled **internal engineering/demo tool** — not styled as part of
the Merchant Portal's real workflow, and nav-labeled "Sandbox (Demo)" so it's obviously
not production checkout. Recommend confirming with product whether this ships to real
merchants at all or stays dev-only (e.g. behind an environment flag) before Task 4 is
picked up for real — this doc records the task as specified, not as a design decision
already made.

### `PaymentService` addition
- `createPayment(req: CreatePaymentRequest, opts: { forceLedgerFailure?: boolean })` —
  **REAL** `POST /api/v1/payments`, setting `X-Force-Ledger-Failure: true` when
  `opts.forceLedgerFailure` (per Catalog Conventions — header form, not just the body
  flag, so the orchestrator path is exercised identically to how the merchant's real
  backend would trigger it).

### `CheckoutDemoComponent` (`features/sandbox/checkout-demo.component.ts`)
**Signal state**:
```ts
amountMinor = signal(21400);
currency = signal<'USD'|'EUR'|'GBP'>('USD');
cardNumber = signal('4242424242424242');
forceLedgerFailure = signal(false);
private submitState = signal<ViewState<{ paymentId: string }>>({ loading: false, error: null, data: null });
private pollState = signal<ViewState<PaymentViewEnriched>>({ loading: false, error: null, data: null });
```
- Quick-fill buttons for `4242` (approved), `0002`/`0069` (declined, two distinct
  reasons per the Catalog's mock gateway), `0119` (processor timeout) — each just sets
  `cardNumber`, doesn't auto-submit.
- "Force Ledger Failure" toggle bound to `forceLedgerFailure`.
- On submit: `createPayment()` (sets `submitState`), then on success start polling
  `PaymentService.getPayment(paymentId)` every 1.5s into `pollState` until `status`
  settles (`CAPTURED`/`DECLINED`/`REFUNDED`) or a max-attempts timeout, visually
  narrating the saga's async nature rather than faking an instant result — this is the
  component's entire pedagogical point (showing compensation to `REFUNDED` happen for
  real when `forceLedgerFailure` is set).

**4 UI states**, applied to the *submission result* panel (the form itself is always
interactive, not a 4-state async view):
- **Loading**: `submitState.loading || pollState.loading` → "Submitting…" then
  "Processing (saga running)…" with the current polled status if any.
- **Error**: `createPayment()` failing outright (e.g. 5xx from the `0119` simulated
  timeout path) vs. the poll timing out without reaching a terminal status — distinct
  copy for each.
- **Empty**: N/A — there is no "zero results" shape for a single submission; omit by
  design, noted explicitly rather than silently skipped, since the skill mandates
  considering all four states per view even when one doesn't apply.
- **Success**: final settled `PaymentView` (status pill + `providerRef`/
  `declineReason` if applicable) with a "View in Transactions" link to
  `/transactions/:id`.

---

## Task 5 — Operator Console: Ledger Integrity & Saga Events

### `LedgerService` addition
- `getLedgerIntegrity()` — **REAL** `GET /operator/ledger/integrity` (map's clean 🟢
  match for this view).

### `OperatorService` addition
- `listSagaEvents()` — **REAL** `GET /operator/saga-events`. Per the map's §7 finding,
  render exactly the real shape (`workflowId`/`status`/`startTime`/`closeTime`) — do
  **not** fabricate the HTML's free-text "Event" narrative column
  ("Ledger write failed → compensating refund issued (6b)"), since nothing in
  `SagaEventResponse` backs it. Replace it with a computed "Duration"
  (`closeTime - startTime`, or "In progress" while `closeTime` is null) — a real,
  derivable column — and keep "Transaction" labeled as "Workflow ID" rather than
  implying a 1:1 payment-ID mapping that the Catalog doesn't guarantee.

### `LedgerIntegrityComponent` (`features/operator/ledger-integrity/`)
**Signal state**:
```ts
private state = signal<ViewState<LedgerIntegrityResponse>>({ loading: true, error: null, data: null });
balanceDeltaMinor = computed(() => {
  const d = this.state().data;
  return d ? d.totalDebitsMinor - d.totalCreditsMinor : null;
});
```
Per the map's explicit UI-wiring note: compute the delta and the ✓/✗ icon from
`balanced`/the delta — never hardcode "$0.00" + a green check the way the static HTML
does.

- **Loading**: banner skeleton.
- **Error**: inline retry banner (an operator needs this to be unmissable — this view
  is a financial-integrity check).
- **Empty**: N/A — the integrity response is a single always-present object, never an
  empty collection; a `0`/`0` balanced result is still a valid Success render, not Empty.
- **Success**: balanced/unbalanced banner with real delta; platform account cards —
  **STUB(BE-01's ledger-routing half, tracked separately since balances need both
  gateway routing AND stay 🟠 until then)** for the five platform account balances
  (Provider settlement, Platform bank, Platform fee revenue, Merchant payable
  all-merchants, Payout in-transit all-merchants); platform double-entry log table —
  **STUB(BE-06)**, since no such endpoint exists at all yet, gateway-routed or not.

### `SagaEventsComponent` (`features/operator/saga-events/`)
**Signal state**: single `ViewState<SagaEventResponse[]>`; no client-side filtering
needed (the HTML has none for this view).
- **Loading**: skeleton rows.
- **Error**: retry banner.
- **Empty**: "No saga executions recorded" (legitimately possible — a clean system
  under light load may have zero failed/compensated sagas, which is good news, not an
  error — copy should reflect that, not read as alarming).
- **Success**: table with the real 4 columns described above.

---

## Task 6 — Operator Console: Provider Routing & Webhook DLQ

### `OperatorService` additions
- `listProviders()` — **REAL** `GET /operator/providers`.
- `updateProviderRouting(id, patch: { priority?: number; active?: boolean })` —
  **REAL** `PATCH /operator/providers/{id}/routing`.
- `getProviderHealth(id)` — **STUB(BE-07)**, merged client-side onto the real
  `ProviderConfig[]` via a `computed()` join (don't block the real toggle on the fake
  health data — they render independently, toggle stays REAL and interactive even if
  the health stub is still loading).
- `listWebhookDlq()` — **REAL** `GET /operator/webhooks/dlq`.
- `retryWebhookDelivery(id)` — **REAL** `POST /operator/webhooks/dlq/{id}/retry`.

### `ProviderRoutingComponent` (`features/operator/provider-routing/`)
**Signal state**:
```ts
private providersState = signal<ViewState<ProviderConfig[]>>({ loading: true, error: null, data: null });
private healthState = signal<ViewState<Record<string, ProviderHealth>>>({ loading: true, error: null, data: null });
pendingToggleIds = signal<Set<string>>(new Set()); // per-row optimistic-lock while a PATCH is in flight
```
Toggling a provider sets its id into `pendingToggleIds` (disables that row's switch),
calls `updateProviderRouting`, and on success replaces that one row in
`providersState` with the server's returned `ProviderConfig` (not a blind local flip —
trust the response). Also **surface `priority`** read-only next to each provider name:
per the map, the backend already supports reading/patching it but the HTML never
displays it at all — a real gap in the *other* direction worth closing in this pass
since the field is already on the wire.

- **Loading**: skeleton provider cards.
- **Error**: retry banner; a failed *toggle* (as opposed to failed initial load) shows
  a transient error only on that provider's card, reverting its switch, rather than
  tearing down the whole view.
- **Empty**: "No providers configured" (operationally alarming if it ever happens —
  copy should say so, unlike the saga-events empty state).
- **Success**: provider cards with name/id, active toggle, (stubbed) health pill +
  success-rate/latency stats, read-only priority, and the routing-note sentence derived
  client-side from the two providers' active state (no API needed — matches the HTML's
  existing client-derived note).

### `WebhookDlqComponent` (`features/operator/webhook-dlq/`)
**Signal state**: `ViewState<WebhookDeliveryResponse[]>` + `retryingIds =
signal<Set<string>>(new Set())` for per-row retry-in-flight state.
- **Loading**: skeleton rows.
- **Error**: retry banner.
- **Empty**: "No dead-lettered deliveries" — a *good* empty state (nothing stuck in the
  DLQ), phrase accordingly.
- **Success**: table (event type, response code, attempt count, next retry time) with a
  per-row "Retry now" button calling `retryWebhookDelivery(id)`, replacing that row with
  the server's updated response on success.

**Note for `DevelopersComponent`** (merchant-facing, not operator): per the map's §8
finding, this view's "Recent deliveries" table is a *different* endpoint
(all deliveries, not just DLQ, and merchant-scoped) — **STUB(BE-08)**, not a re-use of
`listWebhookDlq()`. The API-key list panel is **STUB** with no BE ticket at all (see
`BACKEND_TASKS.md`'s "Explicitly deferred" section — there's no auth-model concept to
target yet); render it clearly as placeholder/mock data, not as a feature awaiting a
specific ticket number, since none exists.

---

## Task 7 — Overview Dashboard & Analytics

### `PaymentService` additions
- `getSummary(days = 30)` — **STUB(BE-04)**.
- `listPayments({ page: 0, size: 6 })` — reuse Task 3's method for the "Recent
  transactions" preview table (same STUB, just a smaller page size) rather than adding
  a second method.

### `OverviewComponent` (`features/overview/`)
**Signal state**:
```ts
private summaryState = signal<ViewState<PaymentsSummary>>({ loading: true, error: null, data: null });
private recentState = signal<ViewState<PaymentViewEnriched[]>>({ loading: true, error: null, data: null });
```
Two independent `ViewState`s again — stat tiles and the recent-transactions table load
and fail independently (matches Task 3's detail/timeline split).

- **Loading**: 5 skeleton stat tiles + skeleton chart area + skeleton table rows.
- **Error**: stat-tile row shows a single banner ("Analytics unavailable") rather than
  5 separate broken tiles; the recent-transactions table gets its own independent retry
  banner.
- **Empty**: recent-transactions table empty state ("No transactions yet") when
  `recentState().data` is `[]` — realistic for a brand-new merchant account, distinct
  copy from the "no filter matches" Empty state in Task 3.
- **Success**: 4 stat tiles (net volume, success rate, avg. auth time — **Active
  disputes tile is explicitly dropped**, per `BACKEND_TASKS.md`'s note that disputes
  aren't a modeled concept anywhere; don't ship a tile bound to nothing), the 14-day
  volume chart (simple inline SVG sparkline/bar rendering off `summaryState().data
  .dailyVolume`, no charting library dependency for 14 data points), and the
  recent-transactions table linking into Task 3's detail route.

---

## Task 8 — Merchant Portal: Ledger & Developers

**Context**: a post-Task-7 audit against `PayFlow Console.html`'s own view sections
(`grep -n 'id="view-'`) found 8 distinct views; Tasks 1–7 only ever assigned 6 of them.
`#view-ledger` (merchant Ledger — payable/in-transit balances, double-entry log,
Request Payout) and `#view-developers` (API keys, webhook URL, recent deliveries) were
never covered — a gap in this plan's original scoping, not an execution miss. Both
routes and nav entries already exist (`/ledger`, `/developers`) and currently render
`PlaceholderView`; this task replaces them with real components.

The backend has moved since `BACKEND_TASKS.md` was first written: `GET
/accounts/{merchantId}/{accountName}/balance`, `POST /payouts`, `GET /payouts/{id}`,
`GET /ledger?transaction=...`, and `GET /webhooks/deliveries` (BE-08) are all
gateway-routed now — confirmed directly against `PaymentGatewayController.java` and the
downstream `LedgerController`/`PayoutController`/`NotificationController` sources. Most
of this task is REAL, not STUB. Two gaps remain genuinely open and need new backend
tickets (add both to `BACKEND_TASKS.md` when this task is picked up):

- **BE-09** — no endpoint returns all ledger entries for one merchant across multiple
  transactions. `GET /ledger?transaction=...` is scoped to a single `transactionRef`;
  `/operator/ledger/entries` (BE-06) is platform-wide, operator-only, and has no
  merchant filter. Needed for the Ledger view's double-entry log table.
- **BE-10** — no endpoint lists a merchant's payout history. `PayoutController` only has
  create (`POST /payouts`), get-by-id (`GET /payouts/{id}`), and sweep-all-merchants
  (`POST /payouts/sweep`) — nothing merchant-scoped and plural.

### `LedgerService` additions
- `getMerchantBalances(merchantId)` — **REAL**, two parallel calls to `GET
  /accounts/{merchantId}/{accountName}/balance` (`accountName` = `"Merchant Payable"`
  and `"Payout In-Transit"` — the exact names seeded by `LedgerAccountService`), joined
  with `forkJoin` into one view-model, same pattern as Task 5's
  `getPlatformAccountCards()`.
- `getLedgerEntriesForTransaction(transactionRef)` — **REAL** `GET
  /ledger?transaction=...`, reusing the `LedgerEntryResponse` type from Task 5.
- `getMerchantLedgerEntries(merchantId)` — **STUB(BE-09)**. Same `LedgerEntryResponse[]`
  shape as the real type, so flipping this to real later is a one-line body change with
  no caller-side impact, per the Service Adapter convention.
- `requestPayout(merchantId)` — **REAL** `POST /payouts`.
- `getPayout(id)` — **REAL** `GET /payouts/{id}`, used to poll the just-requested
  payout's status.

### `LedgerComponent` (`features/ledger/ledger.ts`)
**Signal state**:
```ts
private balancesState = signal<ViewState<{ payableMinor: number; transitMinor: number }>>(initialViewState());
private entriesState = signal<ViewState<LedgerEntryResponse[]>>(initialViewState());
private payoutState = signal<ViewState<PayoutResponse>>({ loading: false, error: null, data: null });
```
Three independent `ViewState`s, matching Task 5's Ledger Integrity split — balances,
the entry log, and the payout action load/fail independently.

**Request Payout flow** mirrors Task 3's refund pattern exactly: call
`requestPayout(merchantId)`, then poll `getPayout(id)` on an interval (e.g. every 2s,
capped attempts) until `status` reaches a terminal value, narrating "In-Transit…" while
polling rather than optimistically jumping to "Settled" — the HTML's existing
`setTimeout`-faked 2.4s transition is exactly the anti-pattern the map already called
out for the refund button; the same fix applies here.

- **Loading**: skeleton balance cards + skeleton entry rows.
- **Error**: independent retry banners for balances vs. the entry log; a failed payout
  request shows its own inline error without disturbing the balance cards.
- **Empty**: entry log empty state ("No ledger entries yet") when `entriesState().data`
  is `[]` — realistic for a merchant with no settled transactions.
- **Success**: both balance cards (Merchant payable, Payout in-transit) with real
  balances; the double-entry log table — **STUB(BE-09)** until that ships, rendered
  with the same honest `isMocked`-style treatment Task 5 used for its two mocked
  platform cards, never silently passed off as real; "Request Payout" button, disabled
  while a request is in flight.

### `DevelopersComponent` (`features/developers/developers.ts`)
#### `OperatorService` addition (despite the merchant-only scope — see note)
- `listWebhookDeliveries(merchantId)` — **REAL** `GET /webhooks/deliveries` (BE-08,
  confirmed gateway-routed; requires `X-Merchant-Id`, which the gateway injects
  automatically for `ROLE_MERCHANT` callers, so no manual header needed client-side).
  Placed on `OperatorService` rather than a new fifth service, since that's already the
  adapter's home for `/webhooks/**` calls (Task 6's DLQ methods) and the Conventions
  fix the adapter layer at exactly four services — a naming mismatch (a merchant-facing
  call living on "Operator"Service) is the accepted tradeoff over growing a fifth
  service for one method.

**Signal state**:
```ts
private deliveriesState = signal<ViewState<WebhookDeliveryResponse[]>>(initialViewState());
```
Reuses the `WebhookDeliveryResponse` type from Task 6 — confirmed byte-for-byte
identical shape on the real `/webhooks/deliveries` response.

- **API keys panel**: no backend concept exists at all (`BACKEND_TASKS.md`'s
  "Explicitly deferred" section — JWT bearer-token auth has no per-merchant key-pair
  model). Render static, clearly-labeled mock data (e.g. one test key, one live key,
  captioned "Mocked — no backend concept yet"), the same honesty treatment the
  Environment toggle got in Task 2 — not wired to any service method, since there is
  nothing to wire to.
- **Webhook endpoint URL**: same treatment — static display + a "Copy URL" button
  (clipboard write only, no network call), captioned as mocked. No registration
  endpoint exists (same deferred list).

**4 UI states** (apply to the "Recent deliveries" table only — the API-keys and
webhook-URL panels are static mock content, not async views, so Loading/Error/Empty
don't apply to them; stated explicitly rather than skipped silently, same as Task 4's
Empty-state call):
- **Loading**: skeleton rows.
- **Error**: retry banner.
- **Empty**: "No webhook deliveries yet."
- **Success**: table (event type, status, attempt count, delivered/attempted time)
  pulling real merchant-scoped data — unlike the operator DLQ view, this one isn't
  limited to dead-lettered rows.

---

## Task 9 — Logout Button & Browser Tab Branding

**Context**: two small, user-requested gaps, neither covered by the original HTML
mockup (so there's no `PayFlow Console.html` section to port from — these are net-new
beyond that source):

1. `AuthService.revokeAndLogout()` has existed since Task 1, but **nothing in the UI
   ever calls it** — confirmed by grepping every `.html`/`.ts` file for the method
   name: its only call sites are internal (the interceptor's failed-refresh path).
   There is currently no way for a user to voluntarily sign out.
2. The browser tab still shows the Angular CLI's default scaffold branding —
   `index.html`'s `<title>` is the generic `PayflowConsole` and the favicon is the
   default `favicon.ico` Angular generates on `ng new`, not the PayFlow mark used
   throughout the app itself (the rounded-square-outline-plus-chevron SVG, confirmed
   inlined in exactly two places today — `sign-in.html` and `app-shell.html`'s rail
   head — via `grep -rl 'viewBox="0 0 26 26"' frontend/src/app`).

### Logout button
No new service method needed — `AuthService.revokeAndLogout()` already does the full
job (POST `/auth/revoke`, clears `_state`/`sessionStorage`, navigates to `/signin`,
all in its own `finalize()`). This task is UI-only.

- **Placement**: `AppShell`'s `rail-foot` block (`app-shell.html`), next to the
  existing identity display (`roleLabel()`/`identityLabel()`) — the natural extension
  of where identity already lives, not a new header element.
- **Component change**: add to `AppShell` (or a new tiny `LogoutButton` sub-component
  if `AppShell` is getting crowded — judgment call, either is fine):
  ```ts
  protected readonly signingOut = signal(false);
  protected signOut(): void {
    if (this.signingOut()) return;
    this.signingOut.set(true);
    this.authService.revokeAndLogout().subscribe({
      error: () => this.signingOut.set(false), // finalize() still navigates away even on error
    });
  }
  ```
  Mirrors `RoleSwitcher`'s existing `switching` signal pattern exactly — same
  disable-while-in-flight shape, nothing new architecturally.
- **No confirm() dialog** — matches this codebase's established convention (the real
  refund button in `TransactionDetail` doesn't use one either, unlike the original
  static HTML's `confirm()`; this app relies on clear button labeling instead). Keep
  logout consistent with that, not with the HTML mockup's abandoned pattern.
- **4 UI states**: N/A — this is a single fire-and-forget action with no data to
  load/empty, same category as the Role Switcher in Task 2. The only state is the
  button's own disabled-while-in-flight flag.

### Browser tab branding
- Export the existing inline SVG mark (same geometry already duplicated in
  `sign-in.html`/`app-shell.html`: `viewBox="0 0 26 26"`, rounded-square outline +
  chevron path) to a standalone `public/favicon.svg`, and reference it in
  `index.html` via `<link rel="icon" type="image/svg+xml" href="favicon.svg">`
  (modern-browser SVG favicon support — no external image-conversion tooling needed,
  unlike regenerating the `.ico`). Keep the existing `favicon.ico` as a secondary
  `rel="alternate icon"` for any browser that doesn't support SVG favicons, rather
  than deleting it outright.
- Update `<title>` from `PayflowConsole` to `PayFlow` — matching the wordmark as it
  actually appears everywhere else in the app's own chrome (the rail head's
  `<span>PayFlow</span>`, sign-in's brand mark), not a longer "...Console" variant
  invented for the tab title alone.
- **Explicitly out of scope for this task** (flagging so it isn't assumed included):
  per-route dynamic tab titles (e.g. "PayFlow · Transactions") — not what was asked;
  a static brand title is the full scope here. If per-route titles are wanted later,
  that's a separate, small follow-up via `Title` service + `HeaderBar`'s existing
  route-data read.

---

## Task 10 — Surface Real Backend Error Messages (BE-11/BE-12)

**Context**: BE-11/BE-12 shipped (commits `6e892dd`, `bf1bd60`, `11104ef`) — every error
response across the platform now returns a uniform body,
`ErrorResponse { error: string, status: number, timestamp: string }`. Confirmed
directly against the source, not assumed: the shared record lives in
`payflow-common/.../dto/ErrorResponse.java`, and four new `@RestControllerAdvice`
classes (`LedgerExceptionHandler`, `PaymentQueryExceptionHandler`,
`NotificationExceptionHandler`, `PaymentCommandExceptionHandler`) plus inline
`ErrorResponse.of(...)` call sites in `AuthController`, `JwtAuthenticationFilter`,
`LedgerController`, `PayoutController`, and `ProviderRoutingController` all emit it —
a `grep` across every service's `main` tree turned up zero remaining bare
`.build()`/`Map.of(...)` error bodies. The gateway's `relay()` method is a raw
byte-passthrough (status/content-type/body all forwarded unchanged), so this reaches
the browser exactly as each service wrote it — no gateway-side change needed.

Before this, every one of the Angular app's ~26 HTTP error handlers discarded
whatever the backend actually said and showed a hardcoded per-view string (e.g.
"Couldn't request a payout — try again." even when the real reason — "No available
balance for payout" — was sitting right there in the response body, now that BE-11
put it there deliberately). That's no longer defensible once a reliable contract
exists to read. This task is a pure plumbing fix — **same `ViewState` shape, same
banners, same components** — just smarter message sourcing. Not a UI redesign.

### New shared model + utility (this is the one new abstraction — keep it this thin)
- `core/models/error-response.model.ts`:
  ```ts
  export interface ErrorResponse {
    error: string;
    status: number;
    timestamp: string;
  }
  ```
- `shared/utils/http-error.ts`:
  ```ts
  export function extractErrorMessage(err: unknown, fallback: string): string {
    if (err instanceof HttpErrorResponse && isErrorResponse(err.error)) {
      return err.error.error;
    }
    return fallback;
  }

  function isErrorResponse(body: unknown): body is ErrorResponse {
    return typeof body === 'object' && body !== null && typeof (body as any).error === 'string';
  }
  ```
  Covers network failures, CORS errors, and any non-JSON body gracefully — falls back
  to the caller's existing string rather than showing `undefined` or throwing.

### Apply across every existing error call site
Every `error: () => ...` handler that currently sets a hardcoded string onto a
`ViewState.error` (or a component-level error signal, e.g. `SignIn.error`) gets that
string moved to the `fallback` argument of `extractErrorMessage(err, fallback)`
instead of being the only possible text. Confirmed via
`grep -rn "error: ()" frontend/src/app/features frontend/src/app/layout` — every
feature component has at least one site; **in scope**: `ledger.ts` (4 sites —
balances/entries/payout-request/payout-poll), `transaction-detail.ts` (4 — payment
load/timeline load/refund-submit/refund-poll), `overview.ts` (2), `ledger-integrity.ts`
(3), `provider-routing.ts` (load + the toggle's existing `toggleErrors` inline
message), `webhook-dlq.ts` (load), `saga-events.ts`, `transactions.ts`,
`developers.ts`, `sign-in.ts`, `checkout-demo.ts` (2 — submit/poll).

Two call sites are explicitly **out of scope** — flagging so they aren't assumed
included, since fixing them properly needs a new UI surface, not just a string swap:
- `role-switcher.ts`'s `switchTo()` error handler resets `switching` but shows nothing
  anywhere — a role-switch failure is currently silent to the user. Needs a small
  inline error affordance added first before `extractErrorMessage` has anywhere to put
  its output.
- `webhook-dlq.ts`'s per-row `retry()` error handler has the same gap — clears the
  pending state with no inline message slot to fill.

Worth a quick fast-follow once Task 10 lands, not blocking it — both are small, and
each would reuse the exact pattern `provider-routing.ts`'s `toggleErrors` already
established for per-row errors.

**4 UI states**: N/A as a new category — this corrects the *content* of the existing
Error state everywhere, adds no new state, no new signals beyond the two call sites
noted above (which are follow-up work, not this task).

---

## Task 11 — Reusable Numbered Pagination (Ledger Double-Entry Log)

**Context**: user-reported gap, not from the HTML mockup. The Ledger view's
double-entry log table fetches a page of entries (`getMerchantLedgerEntries()`,
page size now fixed at 20 per the user's own change to `DEFAULT_ENTRIES_SIZE`) but
has **no pager UI at all** — the component discards the response's `page`/
`totalPages`/`totalElements` and keeps only `.content`, so a merchant with more than
20 ledger entries can never see entry 21 onward. Confirmed directly:
`ledger.service.ts`'s `getMerchantLedgerEntries()` unwraps `PageResponse.content` and
returns `Observable<LedgerEntryResponse[]>`, throwing the pagination metadata away
before it ever reaches `LedgerComponent`.

Requested shape: direct access to any page (not just next/prev), current page shown
at full strength, the pages immediately next to it shown de-emphasized. Explicitly
designed here to be **reusable** — a second, equally real candidate for the exact same
gap already exists (`LedgerIntegrity`'s platform-wide double-entry log,
`getLedgerEntries()`, same discard-the-metadata pattern) — noted below as a follow-up,
not pulled into this task, to keep Task 11 scoped to one view's worth of wiring plus
the shared piece both will eventually share.

### Design

**One new reusable, presentational component** — `shared/components/pagination/` —
the first component in this codebase built as a true dumb/presentational unit with
signal-based `input()`/`output()` rather than owning its own service calls. Every
other component so far is "smart" (injects its own service); this one deliberately
isn't, because the whole point is for `LedgerComponent` (and later, others) to own the
page state and the fetch, while this component only renders page-number buttons and
reports clicks:

```ts
@Component({ selector: 'app-pagination', ... })
export class Pagination {
  readonly currentPage = input.required<number>();   // 0-indexed, matches PageResponse.page
  readonly totalPages = input.required<number>();
  readonly pageChange = output<number>();

  protected readonly windowItems = computed(() =>
    buildPageWindow(this.currentPage(), this.totalPages()),
  );

  protected goTo(page: number): void {
    if (page === this.currentPage() || page < 0 || page >= this.totalPages()) return;
    this.pageChange.emit(page);
  }
}
```

**Pure utility, not component-internal logic** — `shared/utils/pagination.ts` +
`pagination.spec.ts` (same split as `buildSparkline`/`formatDuration`/`applyFilters`
elsewhere in this app — windowing math is exactly the kind of thing that belongs in
an isolated, unit-testable function, not inline in a template or component method):

```ts
export type PageWindowItem = { kind: 'page'; page: number } | { kind: 'ellipsis' };

export function buildPageWindow(currentPage: number, totalPages: number): PageWindowItem[] {
  // Always include first (0) and last (totalPages-1) page, plus currentPage ± 1.
  // Collapse any gap larger than 1 into a single ellipsis marker. 1-indexed display
  // numbers are a template-level +1, not something this function needs to know about.
}
```

**Visual treatment — direct answer to "page 1 and 3 should look slightly blurred"
when on page 2**: interpreting "blurred" as *de-emphasized*, not a literal CSS `blur()`
filter — blurring the actual digits would hurt legibility and make them harder to
read before clicking, which fights the "go to any page" goal rather than serving it.
Concretely:
- Current page: reuses the existing `.chip.active` treatment already established for
  Transactions' status filter (`--accent` background, full opacity) — not a new style.
- Adjacent pages (current ± 1): same `.chip` shape, `opacity: 0.55`-ish and a lighter
  weight — visually "there but secondary," clickable at full size (no shrinking — a
  smaller touch target to save a little dimming isn't a good trade).
- First/last page: always rendered even when far from the current page, per "go to any
  page whenever," with an ellipsis (`…`) marker filling the gap — the standard pattern
  for not rendering 40 buttons across 40 pages.
- **A direct "Go to page" number input next to the numbered row** — clicking through
  nearby numbers only gets a user to *adjacent* pages quickly; true "any page, whenever
  he wants" on a large dataset needs a direct-entry escape hatch too. Small, inline,
  submits on Enter.

If "blurred" was meant literally (an actual `filter: blur()` on the non-current page
numbers), flag that back — happy to build it that way instead, but recommending
against it for the legibility reason above.

### `LedgerService` change
`getMerchantLedgerEntries()` stops unwrapping `.content` — returns
`Observable<PageResponse<LedgerEntryResponse>>` instead of
`Observable<LedgerEntryResponse[]>`, so `page`/`totalPages`/`totalElements` survive to
the component. (`getLedgerEntriesForTransaction()` stays as-is — that one's never
paginated, it's a small single-transaction result set by nature.)

### `LedgerComponent` change
```ts
protected readonly entriesPage = signal(0);  // 0-indexed
```
`entriesState`'s `data` becomes the full `PageResponse<LedgerEntryResponse>` (or keep
a parallel `entriesTotalPages` computed off it — component's call which reads
cleaner). `loadEntries()` passes `{ page: this.entriesPage() }` to the service call.
The `<app-pagination>` element sits below the table, inside the same `isSuccess`-style
gate as the table itself, wired as
`[currentPage]="entriesPage()" [totalPages]="..." (pageChange)="onEntriesPageChange($event)"`,
where `onEntriesPageChange` sets `entriesPage` and calls `loadEntries()` again — explicit
re-fetch on click, not an `effect()` watching the page signal (keeps the "no signal
writes inside effect()" discipline this app has held since Task 1, and matches how
every other data-reload-on-user-action flow already works here, e.g. `loadBalances()`/
`loadEntries()`'s existing retry buttons).

**4 UI states**: unchanged — same `entriesLoading`/`entriesError`/`entriesEmpty`
computeds already built for this table in Task 8. Changing page re-enters the Loading
state the same way a retry click already does; no new state category, no new ViewState
shape beyond widening what `data` holds.

### Explicitly flagged as a follow-up, not in this task
`LedgerIntegrity`'s platform-wide double-entry log (`getLedgerEntries()`) has the
identical discard-the-metadata gap and is the obvious next `<app-pagination>` consumer
— same component, same utility, same wiring pattern, just a different service method
and a different (operator-facing) table. Small follow-up once this lands, not bundled
in here so Task 11 stays reviewable as one view's worth of change.

---

## Task 12 — Pagination Follow-Up: Ledger Integrity's Platform Double-Entry Log

**Context**: the exact gap Task 11 fixed on the merchant Ledger view, confirmed to
also exist on `LedgerIntegrity` — `ledger.service.ts`'s `getLedgerEntries()` (BE-06,
`GET /operator/ledger/entries`) unwraps `PageResponse.content` and discards
`page`/`totalPages`/`totalElements` exactly like `getMerchantLedgerEntries()` did
before Task 11, and `ledger-integrity.ts`'s `entriesState` holds a bare
`LedgerEntryResponse[]` with no pager anywhere in the template. No new component, no
new utility — this task is purely applying Task 11's already-built, already-reviewed
pieces to a second view.

### `LedgerService` change
`getLedgerEntries()` stops unwrapping `.content` — returns
`Observable<PageResponse<LedgerEntryResponse>>`, same change as
`getMerchantLedgerEntries()` got in Task 11. Signature otherwise unchanged
(`since`/`page`/`size` filter stays as-is).

### `LedgerIntegrity` change
Same shape as `Ledger`'s own change in Task 11:
- `entriesState` becomes `signal<ViewState<PageResponse<LedgerEntryResponse>>>`.
- `entries`/`entriesTotalPages` computed off `.data?.content` / `.data?.totalPages`.
- `entriesPage = signal(0)`, passed into `loadEntries()`'s call to the service.
- `<app-pagination [currentPage]="entriesPage()" [totalPages]="entriesTotalPages()"
  (pageChange)="onEntriesPageChange($event)" />` under the table, same gating
  (`!entriesLoading() && !entriesError() && entries().length > 0`) as `ledger.html`
  uses — import `Pagination` into the component's `imports` array.

**4 UI states**: unchanged — same `entriesLoading`/`entriesError`/`entriesEmpty`
computeds this view already has from Task 5, same as Task 11's note for `Ledger`: a
page change re-enters Loading the same way the existing retry button already does, no
new state category.

**Explicitly not revisited in this task**: the integrity banner, the five platform
account cards, and the balanced/unbalanced check above the table are untouched — this
task is scoped to the double-entry log table only.

---

## Cross-cutting items validated at the end of Task 12 (not a separate task)

- Confirm every STUB method in `PaymentService`/`LedgerService`/`OperatorService`
  carries a `// TODO: Wire to backend once BE-xx is merged` comment referencing the
  matching ticket in `BACKEND_TASKS.md` — this is the grep-able signal for "what's left
  to flip to real" once the backend backlog starts closing.
- Confirm every routed view's smart component follows the `ViewState<T>` /
  `isLoading`/`hasError`/`isEmpty`/`isSuccess` convention from Task 1 — no bespoke
  per-component loading boolean sprinkled in later out of convenience.
- Confirm the sign-in screen (`SignInComponent`, not detailed as its own task since it's
  a single form + `AuthService.login()` call already specified in Task 1) maps its form
  to the real `TokenRequest` shape (`role`, optional `merchantId`) rather than the
  HTML's current email/password fields, which per the map's §0 finding don't correspond
  to the real auth model at all.
