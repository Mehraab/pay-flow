package dev.payflow.cqrs.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import dev.payflow.cqrs.bus.CommandBus;
import dev.payflow.cqrs.domain.AggregateRoot;
import dev.payflow.cqrs.exception.DomainException;
import dev.payflow.cqrs.persistence.AggregateRepository;
import org.junit.jupiter.api.Test;

/**
 * Enforces AGENT_AND_DEV_GUIDELINES.md, Rule 1 (Domain Purity): zero framework dependencies
 * inside domain aggregates, entities, and value objects. This rule is mechanical, not a matter
 * of review discipline — a red build here blocks the merge.
 *
 * <p>Uses {@code importPackagesOf} (one representative class per package) rather than {@code
 * importPackages(String)} — the latter resolves classpath entries via the thread's context
 * classloader, which some Gradle test-worker configurations don't populate the way a plain
 * classpath app expects, silently importing zero classes. {@code importPackagesOf} resolves from
 * each given class's own classloader instead, which is robust in that setup.
 */
class DomainPurityArchitectureTest {

  private static final JavaClasses CLASSES =
      new ClassFileImporter()
          .importPackagesOf(
              AggregateRoot.class, CommandBus.class, AggregateRepository.class, DomainException.class);

  @Test
  void domainPackageMustNotDependOnPersistenceSpringOrKafka() {
    ArchRule rule =
        noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "jakarta.persistence..", "javax.persistence..", "org.springframework..", "org.apache.kafka..");

    rule.check(CLASSES);
  }
}
