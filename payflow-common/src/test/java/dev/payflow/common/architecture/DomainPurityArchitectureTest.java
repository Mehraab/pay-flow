package dev.payflow.common.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import dev.payflow.common.domain.Money;
import org.junit.jupiter.api.Test;

/**
 * Enforces AGENT_AND_DEV_GUIDELINES.md, Rule 1 (Domain Purity) for payflow-common's own {@code
 * domain} package. common-cqrs's identically-named test only scans common-cqrs's own classes —
 * ArchUnit only sees what it's explicitly pointed at — so every module that grows a {@code
 * ..domain..} package needs this same mechanical check wired up for itself. This is the first
 * one beyond common-cqrs; Payment's future domain package (Milestone 1, Chunk 2+) gets the same
 * treatment.
 */
class DomainPurityArchitectureTest {

  private static final JavaClasses CLASSES = new ClassFileImporter().importPackagesOf(Money.class);

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
