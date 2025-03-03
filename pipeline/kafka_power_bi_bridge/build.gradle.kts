plugins {
	java
	id("org.springframework.boot") version "3.4.3"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "org.lurence"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter:3.4.3")
	implementation("org.springframework.boot:spring-boot-starter-web:3.4.3")

	implementation("org.springframework.kafka:spring-kafka:3.3.3")

	testImplementation("org.springframework.boot:spring-boot-starter-test:3.4.3")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.12.0")
}

tasks.withType<Test> {
	useJUnitPlatform()
}
