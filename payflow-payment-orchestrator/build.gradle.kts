plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    implementation(project(":common-cqrs"))
    implementation(project(":payflow-common"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // PaymentWorkflow + its activities land here starting Milestone 1 — see
    // ARCHITECTURE_AND_PLAN.md §1.2.
    implementation("io.temporal:temporal-sdk:1.26.1")
    testImplementation("io.temporal:temporal-testing:1.26.1")
    // TestWorkflowEnvironment starts an in-process Temporal test server via this builder at
    // runtime, but temporal-testing doesn't declare it as a transitive dependency.
    testImplementation("io.grpc:grpc-inprocess:1.83.1")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    // Spring Boot 4.x split test-slice annotations (@WebMvcTest, etc.) out of
    // spring-boot-starter-test into per-stack starters.
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
}
