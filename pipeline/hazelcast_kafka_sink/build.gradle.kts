plugins {
    id("java")
    id("application")
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "org.lurence"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()

    maven {
        url = uri("https://packages.confluent.io/maven/")
    }
}

dependencies {
    implementation("com.hazelcast:hazelcast:5.5.0")
    implementation("com.hazelcast:hazelcast-jdbc:5.5.0")
    implementation("com.hazelcast.jet:hazelcast-jet-kafka:5.5.0")
    implementation("com.hazelcast:hazelcast-sql:5.5.0")

    implementation("org.apache.kafka:kafka-clients:3.9.0")
    implementation("org.apache.kafka:connect-json:3.9.0")

    implementation("org.slf4j:slf4j-api:2.0.16")
    implementation("org.slf4j:slf4j-simple:2.0.16")

    implementation("org.apache.avro:avro:1.12.0")
    implementation("io.confluent:kafka-schema-registry-client:7.8.0")
    implementation("io.confluent:kafka-avro-serializer:7.8.0")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

application {
    mainClass.set("org.lurence.JetJob")
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "org.lurence.JetJob"
    }
}

tasks.shadowJar {
    archiveClassifier.set("")
    mergeServiceFiles()
}