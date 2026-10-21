plugins {
    `java-library`
}

dependencies {
    api(project(":common-cqrs"))
    testImplementation("com.tngtech.archunit:archunit-junit5:1.4.1")
}
