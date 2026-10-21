package dev.payflow.paymentcommand;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared base for any test that needs a real Postgres with the Flyway schema applied. The
 * container is started once, in a static initializer, and never stopped — the standard
 * "singleton container" pattern for sharing one Postgres across several test classes in the same
 * JVM, cleaned up by Testcontainers' own Ryuk reaper when the JVM exits. {@code @Container} /
 * {@code @Testcontainers} per-class lifecycle management does NOT work safely here: a static
 * field inherited by multiple subclasses gets stopped after the first subclass's tests finish,
 * then isn't restarted for the next subclass — which is exactly what broke this test the first
 * time it was tried. Matches ARCHITECTURE_AND_PLAN.md §4's testing strategy: real Postgres in
 * tests, not a mocked repository.
 */
public abstract class AbstractPostgresIntegrationTest {

  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

  static {
    POSTGRES.start();
  }

  @DynamicPropertySource
  static void postgresProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }
}
