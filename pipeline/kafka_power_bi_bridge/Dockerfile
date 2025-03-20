# Use a base image with JDK
FROM amazoncorretto:21-alpine3.21

# Set a working directory in the container
WORKDIR /app

# Copy the Gradle wrapper and build file
COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts .
COPY settings.gradle.kts .

RUN chmod +x ./gradlew


# Copy the source code
COPY src src

# Use the Gradle wrapper to run the application, avoiding the daemon
ENTRYPOINT ["sh", "-c", "./gradlew --no-daemon bootRun"]
