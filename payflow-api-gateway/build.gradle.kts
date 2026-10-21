plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    // Spring Boot 4.x split TestRestTemplate out of spring-boot-test into its own module —
    // plain spring-boot-starter-test no longer brings it in.
    testImplementation("org.springframework.boot:spring-boot-resttestclient")
    // TestRestTemplate needs these at runtime to build its HTTP client; neither is pulled in
    // transitively by spring-boot-resttestclient.
    testImplementation("org.springframework.boot:spring-boot-http-client")
    testImplementation("org.springframework.boot:spring-boot-restclient")
}
