# Stage 1: Build the JAR file
FROM gradle:jdk21 as build

WORKDIR /app

# Copy the Gradle files and source code
COPY build.gradle.kts settings.gradle.kts gradlew gradle.properties ./
COPY gradle gradle
COPY src src

# Build the project using Gradle
RUN ./gradlew build

# Stage 2: Run the JAR file using JDK 21
FROM openjdk:21-jdk

WORKDIR /app

# Copy the JAR file from the build stage
COPY --from=build /app/build/libs/*.jar app.jar

# Set the entry point
ENTRYPOINT ["java", "-jar", "app.jar"]