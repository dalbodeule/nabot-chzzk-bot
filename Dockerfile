# Builder Stage
FROM gradle:jdk-21-and-22-graal-jammy AS builder

# Set working directory
WORKDIR /app

# Copy Gradle wrapper and build scripts
COPY gradlew ./
COPY gradle ./gradle
COPY build.gradle.kts .
COPY settings.gradle.kts .
COPY gradle.properties .

# Download dependencies
RUN ./gradlew --no-daemon dependencies

# Build the application
RUN ./gradlew build

# Copy the source code
COPY src ./src

# Build the application
RUN ./gradlew nativeCompile

# Runner Stage
FROM alpine:latest AS runner

# Set working directory
WORKDIR /app

# Copy the native image from the builder stage
COPY --from=builder /app/build/native/nativeCompile/chzzk_bot .

# Ensure the application binary is executable
RUN chmod +x /app/chzzk_bot

# Run the application
CMD ["./chzzk_bot"]