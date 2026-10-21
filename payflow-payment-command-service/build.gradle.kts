plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    implementation(project(":common-cqrs"))
    implementation(project(":payflow-common"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
    implementation("org.postgresql:postgresql")
    // Spring Boot 4.x split Flyway autoconfiguration out of the raw flyway-core library too —
    // plain flyway-core alone never ran any migration.
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    // Spring Boot 4.x split Kafka autoconfiguration out of the raw spring-kafka library into its
    // own starter — plain spring-kafka alone creates no KafkaTemplate bean.
    implementation("org.springframework.boot:spring-boot-starter-kafka")
    implementation("tools.jackson.core:jackson-databind")
    implementation("io.temporal:temporal-sdk:1.26.1")
    // Reuses the PaymentWorkflow contract (interface + input record) the orchestrator defines —
    // this service is a Temporal client of that workflow, not its own second copy of the type.
    implementation(project(":payflow-payment-orchestrator"))

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    // Explicit version: spring-boot-dependencies:4.1.1 points testcontainers.version at 2.0.5,
    // which isn't actually published to Maven Central yet — pinned to the latest real release.
    // Wired manually via @DynamicPropertySource (AbstractPostgresIntegrationTest), not
    // spring-boot-testcontainers' @ServiceConnection — see that class's Javadoc for why.
    testImplementation("org.testcontainers:postgresql:1.21.4")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.4.1")
}
