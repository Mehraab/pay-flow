package dev.payflow.paymentcommand;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * A full context load — including the real DataSource/Flyway wiring against the Testcontainers
 * Postgres from {@link AbstractPostgresIntegrationTest} — now that this service genuinely has a
 * DataSource, Redis, and Kafka auto-configured. Redis and Kafka clients connect lazily (on first
 * actual command/operation, not at context refresh), so this stays a cheap smoke test without
 * needing containers for those too; a real end-to-end run against all of them is Milestone 1's
 * documented curl-based verification (ARCHITECTURE_AND_PLAN.md §Milestone 1), not this suite's job.
 */
@SpringBootTest
class PaymentCommandServiceApplicationTests extends AbstractPostgresIntegrationTest {

  @Test
  void contextLoads() {}
}
