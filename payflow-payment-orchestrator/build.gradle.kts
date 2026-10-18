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
    // ARCHITECTURE_AND_PLAN.md §1.2. Declared now so the module tree matches the plan;
    // nothing in this module uses them yet.
    implementation("io.temporal:temporal-sdk:1.26.1")
    testImplementation("io.temporal:temporal-testing:1.26.1")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
