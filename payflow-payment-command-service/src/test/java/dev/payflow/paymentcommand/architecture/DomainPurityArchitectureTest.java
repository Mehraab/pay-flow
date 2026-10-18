package dev.payflow.paymentcommand.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import dev.payflow.paymentcommand.domain.Payment;
import org.junit.jupiter.api.Test;

/**
 * Enforces AGENT_AND_DEV_GUIDELINES.md, Rule 1 (Domain Purity) for {@code
 * dev.payflow.paymentcommand.domain} — the {@link Payment} aggregate and its events must not
 * depend on Spring, JPA/Hibernate, Kafka, or Jackson. Mirrors common-cqrs's and payflow-common's
 * identically-named tests; ArchUnit only sees what it's explicitly pointed at, so every module
 * with a {@code ..domain..} package carries this same mechanical check.
 */
class DomainPurityArchitectureTest {

  private static final JavaClasses CLASSES = new ClassFileImporter().importPackagesOf(Payment.class);

  @Test
  void domainPackageMustNotDependOnPersistenceSpringOrKafka() {
    ArchRule rule =
        noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "jakarta.persistence..",
                "javax.persistence..",
                "org.hibernate..",
                "org.springframework..",
                "org.apache.kafka..",
                "com.fasterxml.jackson..",
                "tools.jackson..");

    rule.check(CLASSES);
  }
}
