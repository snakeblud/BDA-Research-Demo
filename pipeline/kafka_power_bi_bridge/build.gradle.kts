plugins {
    java
    id("org.springframework.boot") version "3.4.3"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "org.lurence"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter:3.4.3")
    implementation("org.springframework.boot:spring-boot-starter-web:3.4.3")

    // Actuator + Prometheus
    implementation("org.springframework.boot:spring-boot-starter-actuator:3.4.3")
    implementation("io.micrometer:micrometer-core")
    implementation("io.micrometer:micrometer-registry-prometheus")

    // Kafka
    implementation("org.springframework.kafka:spring-kafka:3.3.3")
    implementation("org.apache.kafka:connect-json:3.9.0")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test:3.4.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.12.0")
}

// ✅ Kotlin DSL style for JUnit 5
tasks.test {
    useJUnitPlatform()
}