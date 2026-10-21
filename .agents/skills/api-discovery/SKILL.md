# Skill: PayFlow Microservices & Gateway API Discovery

## Objective
Scan the `pay-flow` microservices to document all public endpoints routed through `payflow-api-gateway` (port 8080) and generate `specs/API_CATALOG.md`.

## Architectural Rules
1. **Single Entry Point**:
    - The frontend will ONLY call `http://localhost:8080`.
    - Inspect `payflow-api-gateway/src/main/resources/application.yml` (or its route config) to see how Gateway routes map to downstream services.
    - Inspect Gateway CORS configurations and Auth filters.

2. **Service-by-Service Scan**:
   Scan each microservice's `@RestController` classes and DTOs:
    - `payflow-api-gateway`: Auth endpoints (`/auth/token`, `/auth/refresh`, `/auth/revoke`).
    - `payflow-payment-command-service`: Payment commands (e.g. `POST /api/v1/payments`, refunds).
    - `payflow-payment-query-service`: Payment read queries (e.g. `GET /api/v1/payments`, transaction feeds, status).
    - `payflow-ledger-service`: Accounts, balances (`/accounts/.../balance`), payouts (`/payouts`), and integrity verification.
    - `payflow-payment-orchestrator`: Temporal Saga execution tracking, saga audit events (`/operator/saga-events`).
    - `payflow-provider-gateway-service`: Provider routing toggles (`/operator/providers`).
    - `payflow-notification-service`: Webhook DLQ and retry endpoints (`/operator/dlq`).

3. **Domain Types & Units**:
    - Note monetary field representations (e.g., are amounts in minor units / cents like `21400` or `BigDecimal`?).
    - Extract test card handling / error simulation flags (e.g., headers or payload fields that trigger Temporal Saga compensation to `REFUNDED`).

## Output Contract
Generate `specs/API_CATALOG.md` organized into the 4 target frontend modules:
- Section 1: Auth APIs (`/auth/*`)
- Section 2: Payments APIs (`/api/v1/payments/*` - Command & Query)
- Section 3: Ledger & Payout APIs (`/accounts/*`, `/payouts/*`)
- Section 4: Operator & Saga APIs (`/operator/*`)

For every endpoint, detail:
- Gateway Route URL & HTTP Method
- Target Microservice
- Roles allowed (`ROLE_MERCHANT`, `ROLE_OPERATOR`, or Public)
- Request Body schema / Path params
- Response Body schema