// Deliberately NOT applying org.springframework.boot or io.spring.dependency-management
// here: this module must never carry a Spring, JPA, or Kafka-client dependency, even a
// transitive one. See AGENT_AND_DEV_GUIDELINES.md, Rule 1 — enforced by
// DomainPurityArchitectureTest in this module's own test suite.
plugins {
    `java-library`
}

dependencies {
    testImplementation("com.tngtech.archunit:archunit-junit5:1.4.1")
}
