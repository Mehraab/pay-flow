# PayFlow — Agent & Developer Guidelines

**Status:** Binding v0.1 · **Applies to:** every AI coding agent and every human engineer touching this repository, from Milestone 0.5 onward.

> This is not a style guide. Code that violates a rule below does not merge — not "needs a follow-up," not "fine for a demo." If you are an AI agent implementing a milestone from `ARCHITECTURE_AND_PLAN.md`, treat every rule here as a hard constraint on the code you generate, not a suggestion to weigh against convenience. Where this document and a milestone's task list seem to conflict, this document wins — open a question rather than silently picking one.

## How to use this document

Each rule has the same shape: the rule itself, *why* it exists (tied back to a specific BR/architecture decision, not just "best practice"), a ❌ violation, and a ✅ compliant alternative. The "why" matters — it's what lets you correctly extend a rule to a case this document didn't think to spell out.

---

## Rule 1 — Domain purity

**Zero framework dependencies inside domain aggregates, entities, and value objects.** No `@Entity`, `@Table`, `@Id`, `@Column`, `@Document`, no Spring `@Component`/`@Service`, no Kafka client types, no Jackson annotations — nothing outside the JDK (plus `common-cqrs` itself) may be imported under a `*.domain` package.

**Why:** `AggregateRoot`, `Entity`, and `ValueObject` (`ARCHITECTURE_AND_PLAN.md` §Milestone 0.5) exist specifically so a domain type's logic can be unit-tested in milliseconds with no database, no Spring context, and no Kafka broker — and so the persistence technology (Postgres today) can change without touching a single business rule. A `@jakarta.persistence.Entity` annotation on `Payment` is a one-way door: it quietly makes JPA part of the domain's contract forever.

```java
// ❌ Rule 1 violation — domain type coupled to JPA
@jakarta.persistence.Entity
@jakarta.persistence.Table(name = "payments")
public class Payment extends AggregateRoot<PaymentId, PaymentEvent> {
    @jakarta.persistence.Id
    private UUID id;
    // ...
}
```

```java
// ✅ Rule 1 compliant — pure domain type; JPA lives in the adapter, not here
public final class Payment extends AggregateRoot<PaymentId, PaymentEvent> {
    private Money amount;
    private PaymentStatus status;

    private Payment(PaymentId id, long version) { super(id, version); }

    public static Payment create(PaymentId id, Money amount, IdempotencyKey key) {
        var payment = new Payment(id, 0);
        payment.raise(new PaymentCreated(EventMetadata.next(id, key.correlationId(), null), amount));
        return payment;
    }

    @Override
    protected void apply(PaymentEvent event) {
        switch (event) {
            case PaymentCreated e -> { this.amount = e.amount(); this.status = PaymentStatus.CREATED; }
            case PaymentCaptured e -> this.status = PaymentStatus.CAPTURED;
            // ...
        }
    }
}

// The JPA shape lives entirely in the adapter module, mapped by hand or MapStruct —
// `PaymentJpaEntity` is NOT `Payment`, and nothing in `domain` knows it exists.
@jakarta.persistence.Entity
@jakarta.persistence.Table(name = "payments")
class PaymentJpaEntity { /* ... */ }
```

**Enforcement:** the ArchUnit rule from Milestone 0.5 (`noClasses().that().resideInAPackage("..domain..").should().dependOnClassesThat().resideInAnyPackage("jakarta.persistence..", "org.springframework..", "org.apache.kafka..")`) runs in CI on every module. A red ArchUnit test is a build failure, not a warning.

---

## Rule 2 — Pattern adherence: every write goes through the bus

**All state-mutating operations execute as `CommandBus.dispatch(command)` → a registered `CommandHandler` → `AggregateRepository.save(aggregate)`.** A Spring `@RestController` never contains business logic, never calls a repository directly, and never constructs or mutates an aggregate itself. Its entire job is: deserialize the HTTP request into a `Command` record, dispatch it, serialize the result.

**Why:** this is what makes Rule 4 (idempotency + optimistic locking) and the `CommandInterceptor` chain (`ARCHITECTURE_AND_PLAN.md` §Milestone 0.5) apply uniformly to *every* write with no per-endpoint opt-in — a new endpoint gets idempotency checking and version enforcement for free, by construction, instead of by remembering to add it.

```java
// ❌ Rule 2 violation — controller bypasses the bus and talks to the repository directly
@RestController
class PaymentController {
    private final PaymentRepository repository; // wrong layer to depend on

    @PostMapping("/payments")
    ResponseEntity<?> create(@RequestBody CreatePaymentRequest req) {
        var payment = Payment.create(PaymentId.generate(), req.toMoney(), req.idempotencyKey());
        repository.save(payment);           // no interceptor chain ran — no idempotency check, no version check
        return ResponseEntity.ok(payment.id());
    }
}
```

```java
// ✅ Rule 2 compliant
@RestController
class PaymentController {
    private final CommandBus commandBus;

    @PostMapping("/payments")
    ResponseEntity<?> create(@RequestHeader("Idempotency-Key") String key,
                              @RequestBody CreatePaymentRequest req) {
        var result = commandBus.dispatch(new CreatePaymentCommand(req.merchantId(), req.toMoney(), key));
        return ResponseEntity.status(201).body(result);
    }
}

record CreatePaymentCommand(MerchantId merchantId, Money amount, String idempotencyKey) implements Command {}

@Component
class CreatePaymentHandler implements CommandHandler<CreatePaymentCommand, PaymentId> {
    private final AggregateRepository<Payment, PaymentId, PaymentEvent> repository;

    @Override
    public PaymentId handle(CreatePaymentCommand command) {
        var payment = Payment.create(PaymentId.generate(), command.amount(), command.idempotencyKey());
        repository.save(payment);   // runs inside the interceptor chain — idempotency + optimistic locking applied
        return payment.id();
    }
}
```

**Reads are exempt from this rule's mechanics but not its spirit:** `QueryBus`/`QueryHandler` exist for the same reason — a controller dispatches a `Query<R>`, it does not hand-roll a repository call either. Query handlers may read from a denormalized store directly (that's the entire point of CQRS's read side), but they still go through the bus so cross-cutting concerns (auth, metrics) stay uniform.

---

## Rule 3 — Dual-write prevention: no direct Kafka publishing

**No controller, handler, or any application-layer code ever calls a Kafka producer directly.** The only events that ever reach Kafka are the ones a repository adapter pulls from `aggregate.pullUncommittedEvents()` and writes to `outbox_events` in the same transaction as the aggregate's own row (`ARCHITECTURE_AND_PLAN.md` §1.2, Transactional Outbox). The outbox poller is the only process in the system with a live Kafka producer.

**Why:** a direct `kafkaTemplate.send(...)` call from a controller is exactly the dual-write failure mode the Outbox pattern exists to eliminate — the DB commit and the Kafka publish become two separate network calls with no shared transaction, so a crash between them either loses the event or announces something that was never actually saved. This rule has no exceptions for "it's just a small notification" — the failure mode doesn't care how small the event is.

```java
// ❌ Rule 3 violation — direct publish, no transactional guarantee it matches what was saved
@Component
class CreatePaymentHandler implements CommandHandler<CreatePaymentCommand, PaymentId> {
    private final PaymentRepository repository;
    private final KafkaTemplate<String, Object> kafka;   // should not exist in this class at all

    public PaymentId handle(CreatePaymentCommand command) {
        var payment = Payment.create(...);
        repository.save(payment);
        kafka.send("payment.created", payment.id().toString(), toEvent(payment)); // ← dual write
        return payment.id();
    }
}
```

```java
// ✅ Rule 3 compliant — the adapter's save() is the only place an event is ever persisted,
// and ONLY to outbox_events; nothing in application code imports a Kafka producer type.
class JpaPaymentRepository implements AggregateRepository<Payment, PaymentId, PaymentEvent> {

    @Transactional
    @Override
    public void save(Payment payment) {
        // 1. upsert the aggregate's own row, with a WHERE version = :expectedVersion check
        // 2. for each event in payment.pullUncommittedEvents(): INSERT INTO outbox_events (...)
        // both statements, one transaction — see the outbox DDL in ARCHITECTURE_AND_PLAN.md §2.2
    }
}
```

**The one process allowed to hold a Kafka producer** is the outbox poller itself — a scheduled job that only ever reads `outbox_events` and republishes, never anything derived from request-handling code.

---

## Rule 4 — Concurrency & idempotency on every financial mutation

**Every request that mutates money (create payment, capture, refund, request payout) must: (a) carry and validate an idempotency key before any domain logic runs, and (b) enforce the aggregate's optimistic-locking `version` on save.** Both checks live in `CommandInterceptor`s on the bus (§Rule 2), not duplicated inside individual handlers.

**Why:** BR-2 ("the platform shall never charge the same payment twice") and the `version` field on `AggregateRoot` (§Milestone 0.5) exist for the same underlying risk — at-least-once delivery and network retries mean the *same* mutating request can arrive twice, and two different requests can race to modify the same aggregate concurrently. Idempotency keys solve the first; optimistic locking solves the second. A handler that skips either is financially unsafe regardless of how correct its business logic is.

```java
// ❌ Rule 4 violation — no idempotency check; a retried POST creates a second payment
@Component
class CreatePaymentHandler implements CommandHandler<CreatePaymentCommand, PaymentId> {
    public PaymentId handle(CreatePaymentCommand command) {
        var payment = Payment.create(PaymentId.generate(), command.amount());
        repository.save(payment);
        return payment.id();
    }
}
```

```java
// ✅ Rule 4 compliant — idempotency enforced once, centrally, as an interceptor every command passes through
@Component
class IdempotencyInterceptor implements CommandInterceptor {
    private final RedisIdempotencyStore store;   // Redis, per ARCHITECTURE_AND_PLAN.md §1.1

    @Override
    public <C extends Command, R> R intercept(C command, CommandInvocation<C, R> next) {
        if (command instanceof IdempotentCommand ic) {
            return store.getOrCompute(ic.idempotencyKey(), () -> next.proceed(command));
        }
        return next.proceed(command);
    }
}

// Optimistic locking: the repository adapter's save() compares the loaded version against
// the stored row's current version inside the UPDATE's WHERE clause, and throws
// ConcurrencyException on a mismatch — see the AggregateRepository contract, §Milestone 0.5.
// A handler never catches ConcurrencyException to "merge" changes; it propagates, and the
// client retries the whole command (safe, because of the idempotency key above).
```

---

## Rule 5 — Code style: modern, idiomatic Java 25

**Commands, queries, and events are `record`s. Branch on sealed event/command hierarchies with `switch` pattern matching, not `instanceof` chains or a `type` string field. Use virtual threads for the service layer's blocking I/O (JDBC, outbound REST to internal endpoints, Temporal activity execution) instead of hand-tuned platform-thread pools.**

**Why:** this isn't aesthetic preference — it's what the rest of this document assumes. `DomainEvent` is designed around records implementing an interface (§Milestone 0.5); a `switch` over a `sealed interface PaymentEvent permits PaymentCreated, PaymentCaptured, ...` is exhaustiveness-checked by the compiler, so `AggregateRoot.apply()` cannot silently ignore a new event type the way an `if/else` chain can. Virtual threads matter specifically because this system is deliberately full of blocking calls by design (Temporal activities, internal synchronous REST between services, §1.2) — the whole point of that design is to keep each step simple and observable, which only stays cheap at scale if threads are cheap.

```java
// ❌ Rule 5 violation — mutable command class, string-typed event discrimination
public class CreatePaymentCommand {
    private String merchantId;
    private long amountMinor;
    public void setMerchantId(String id) { this.merchantId = id; }
    // ...
}

void apply(PaymentEvent event) {
    if (event.getType().equals("PaymentCreated")) { /* ... */ }
    else if (event.getType().equals("PaymentCaptured")) { /* ... */ }
}
```

```java
// ✅ Rule 5 compliant
public sealed interface PaymentEvent extends DomainEvent
    permits PaymentCreated, PaymentAuthorized, PaymentCaptured, PaymentFailed, PaymentRefunded {}

public record PaymentCreated(EventMetadata metadata, Money amount, String idempotencyKey) implements PaymentEvent {}
public record PaymentCaptured(EventMetadata metadata, String providerRef) implements PaymentEvent {}
// ... one record per event, no shared mutable base class

@Override
protected void apply(PaymentEvent event) {
    switch (event) {
        case PaymentCreated e    -> { this.amount = e.amount(); this.status = PaymentStatus.CREATED; }
        case PaymentAuthorized e -> this.status = PaymentStatus.AUTHORIZED;
        case PaymentCaptured e   -> this.status = PaymentStatus.CAPTURED;
        case PaymentFailed e     -> this.status = PaymentStatus.FAILED;
        case PaymentRefunded e   -> this.status = PaymentStatus.REFUNDED;
        // sealed interface + exhaustive switch: adding a new event without a case is a compile error
    }
}
```

```java
// Virtual threads: Spring Boot 3.4+/4.x — enable platform-wide, don't hand-roll an Executor per service
spring:
  threads:
    virtual:
      enabled: true
```

---

## Enforcement summary

| Rule | Mechanical check | Where it runs |
|---|---|---|
| 1 — Domain purity | ArchUnit: no `jakarta.persistence`/Spring/Kafka imports under `*.domain` | CI, every module, every build |
| 2 — Pattern adherence | ArchUnit: `@RestController` classes may depend on `CommandBus`/`QueryBus` only, never on `AggregateRepository` or a handler directly | CI |
| 3 — Dual-write prevention | ArchUnit: only the outbox-poller package may depend on the Kafka producer client | CI |
| 4 — Idempotency & concurrency | Integration test per mutating endpoint: same idempotency key twice → one aggregate; concurrent `save()` at a stale version → `ConcurrencyException` | Testcontainers suite, §4 of `ARCHITECTURE_AND_PLAN.md` |
| 5 — Code style | `./gradlew spotlessCheck` (record/sealed-interface conventions) + compiler warnings as errors for non-exhaustive switches | CI |

A pull request — human- or agent-authored — that doesn't pass all five is not ready for review, independent of whether the feature behind it "works."
