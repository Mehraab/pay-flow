plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    implementation(project(":common-cqrs"))
    implementation(project(":payflow-common"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    // Spring Boot 4.x split Kafka autoconfiguration (including @KafkaListener container
    // factories) out of raw spring-kafka into its own starter (discovered in Milestone 1
    // Chunk 2) — plain spring-kafka alone wires no listener infrastructure either.
    implementation("org.springframework.boot:spring-boot-starter-kafka")
    implementation("tools.jackson.core:jackson-databind")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
}
