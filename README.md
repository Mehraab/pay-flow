# PayFlow

A reference payment-processing platform — the same core job Stripe or Adyen do (authorize → capture → settle → payout, with refunds and webhooks), built end-to-end as a demoable, production-credible fintech system.

> **Status:** Milestone 0 + 0.5 complete — infrastructure and the shared CQRS/DDD harness are in place. No business domain code (`Payment`, `LedgerEntry`, `Webhook`, …) exists yet; see the [Milestone Progress](#milestone-progress) checklist below.

## Project overview & core objectives

PayFlow is a merchant payment-orchestration platform: it authorizes and captures card payments on a merchant's behalf, settles funds through an internal double-entry ledger, pays merchants out on request or on a schedule, and notifies merchants of outcomes via webhook — architected and built the way a real payments team would, not as a CRUD demo.

Three objectives drive every decision in this repo:

1. **Credibility.** Every pattern here — transactional outbox, Temporal-orchestrated sagas, optimistic locking, idempotency — exists because real payment systems need it, not because it's resume-shaped.
2. **Finishability.** Scope is deliberately bounded (see `ARCHITECTURE_AND_PLAN.md`, §5 Deferred backlog). A small system that actually runs beats a large one that doesn't.
3. **Demoability.** The system is built in runnable vertical slices (Tracer Bullet architecture) — by the end of Milestone 1, a single `curl` call produces a real charge, a real balanced ledger entry, and a real webhook.

The full business requirements live in the companion BRD/SRS; the full technical design and the milestone-by-milestone build plan live in [`ARCHITECTURE_AND_PLAN.md`](./ARCHITECTURE_AND_PLAN.md). Every engineer and AI agent working in this repo is bound by [`AGENT_AND_DEV_GUIDELINES.md`](./AGENT_AND_DEV_GUIDELINES.md).

## Tech stack & prerequisites

| Concern | Choice |
|---|---|
| Language / runtime | Java 25 (LTS), virtual threads |
| Framework | Spring Boot 3.4+ / 4.x line |
| Write-side store | PostgreSQL 16+ |
| Cache / coordination | Redis 7.4+ |
| Event backbone | Apache Kafka 3.9+, KRaft mode (no ZooKeeper) |
| Orchestration engine | Temporal (official Java SDK) |
| Build | Gradle 8.x, multi-module |

**To work in this repo you need, locally installed:**

- JDK 25 (the Gradle toolchain will use whichever JDK 25 it finds — no manual `JAVA_HOME` juggling required if one is on your `PATH`/SDKMAN)
- Docker + Docker Compose
- Nothing else — Gradle itself is invoked through the committed wrapper (`./gradlew`), so no local Gradle install is required

## Quickstart / local setup

```bash
# 1. Bring up infrastructure: Postgres (3 databases), Redis, Kafka (KRaft), Temporal + its UI
docker compose up -d
docker compose ps   # wait until postgres reports "healthy"

# 2. Build every module and run the full test suite
./gradlew build

# 3. Confirm the harness is sound
./gradlew :common-cqrs:test
./gradlew :common-cqrs:dependencies   # should show no Spring/JPA/Kafka on the classpath

# 4. Open Temporal's Web UI (useful from Milestone 1 onward)
open http://localhost:8088
```

Each service is still a bare `/actuator/health` skeleton at this stage (Milestone 0) — there's no business flow to call yet. Run one with, e.g.:

```bash
./gradlew :payflow-payment-command-service:bootRun
curl -s localhost:8081/actuator/health | jq .status   # "UP"
```

## Architectural highlights

- **Hexagonal architecture, enforced mechanically.** Every service's domain layer depends on nothing but the JDK and `common-cqrs`. Ports (`AggregateRepository`) are defined in the domain; adapters (Postgres, Kafka, Temporal) live outside it. An ArchUnit test fails the build if this boundary is ever crossed — see `AGENT_AND_DEV_GUIDELINES.md`, Rule 1.
- **CQRS, both at the architecture level and inside each service.** Payment Command Service (write, strongly consistent) and Payment Query Service (read, denormalized, eventually consistent, rebuildable from events) are separate deployables — not a flag on one service. Inside each service, every write is `CommandBus → CommandHandler → AggregateRepository` and every read is `QueryBus → QueryHandler`, from the shared `common-cqrs` harness.
- **Transactional Outbox.** A service never calls Kafka directly from request-handling code (`AGENT_AND_DEV_GUIDELINES.md`, Rule 3). The aggregate row and its outbox row are written in one transaction; a poller publishes from the outbox, so a crash can never lose an event or announce one that was never saved.
- **Temporal-orchestrated saga.** The payment lifecycle (`authorizeAndCapture → postToLedger → dispatchWebhook`) runs as one durable Temporal Workflow with explicit compensation activities (`reverseProviderCharge`, `markPaymentFailed`) — not a hand-rolled chain of services reacting to each other's Kafka events. The full execution history is queryable in Temporal UI, which also backs the operator's saga audit feed.
- **Full design detail:** [`ARCHITECTURE_AND_PLAN.md`](./ARCHITECTURE_AND_PLAN.md).

## Repository layout

```
payflow/
  common-cqrs/                       # framework-agnostic DDD/CQRS harness — every service depends on it
  payflow-common/                    # shared Kafka event DTOs (empty until Milestone 1)
  payflow-api-gateway/                    :8080
  payflow-payment-command-service/        :8081
  payflow-payment-query-service/          :8082
  payflow-provider-gateway-service/       :8083
  payflow-payment-orchestrator/           :8084 (Temporal Worker)
  payflow-ledger-service/                 :8085
  payflow-notification-service/           :8086
  infra/postgres-init/               # multi-database bootstrap for the Postgres container
  docker-compose.yml                 # Postgres, Redis, Kafka (KRaft), Temporal Server + UI
```

## Milestone progress

Tracking `ARCHITECTURE_AND_PLAN.md` §3. Check items as each milestone's exit criteria are met.

- [x] **Milestone 0 — Infrastructure & skeleton.** Gradle multi-module root, `docker-compose.yml` (Postgres/Redis/Kafka/Temporal), Postgres multi-db init script, this README.
- [x] **Milestone 0.5 — CQRS & DDD core harness.** `common-cqrs` fully implemented and unit-tested against a throwaway aggregate; ArchUnit domain-purity rule green; zero business types written.
- [ ] **Milestone 1 — The golden path.** `POST /api/v1/payments` with a `4242` test card → captured payment, balanced ledger entry, delivered webhook, end to end.
- [ ] **Milestone 2 — Resiliency & failure flows.** Deterministic failure injection, outbox crash-recovery, consumer replay/dedup, Temporal-driven saga compensation (BR-5).
- [ ] **Milestone 3 — Payout & refund lifecycles.** Manual + automated EOD payouts (BR-15), full refund (BR-16), ledger reconciliation.
- [ ] **Milestone 4 — Security, RBAC & observability.** JWT gateway filter (`ROLE_MERCHANT`/`ROLE_OPERATOR`, BR-11), operator ledger-integrity endpoint (BR-12), webhook DLQ management.

## Engineering rules

Every change in this repo — human- or AI-agent-authored — must comply with [`AGENT_AND_DEV_GUIDELINES.md`](./AGENT_AND_DEV_GUIDELINES.md): domain purity, bus-only writes, no direct Kafka publishing outside the outbox poller, idempotency + optimistic locking on every financial mutation, and modern Java 25 idioms. Several of these are enforced mechanically (ArchUnit, CI) rather than left to review discipline — see that document's enforcement table.
