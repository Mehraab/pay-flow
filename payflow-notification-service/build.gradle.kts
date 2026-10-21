plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    implementation(project(":common-cqrs"))
    implementation(project(":payflow-common"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.postgresql:postgresql")
    // Spring Boot 4.x split Flyway autoconfiguration out of the raw flyway-core library —
    // plain flyway-core alone never ran any migration (discovered in Milestone 1 Chunk 2).
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    // Explicit version: spring-boot-dependencies:4.1.1 points testcontainers.version at 2.0.5,
    // which isn't actually published to Maven Central yet — pinned to the latest real release.
    testImplementation("org.testcontainers:postgresql:1.21.4")
}
