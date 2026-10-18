plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    implementation(project(":common-cqrs"))
    implementation(project(":payflow-common"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    // Spring Boot 4.x split test-slice annotations (@WebMvcTest, etc.) out of
    // spring-boot-starter-test into per-stack starters.
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
}
