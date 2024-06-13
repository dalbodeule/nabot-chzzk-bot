# Builder Stage
FROM ghcr.io/graalvm/graalvm-ce:22.3.3 AS builder

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

# Copy the source code
COPY src ./src

# Build the application
RUN ./gradlew build
RUN ./gradlew nativeCompile

# Runner Stage
FROM woahbase/alpine-glibc:latest AS runner

# Install glibc (required for running Java applications on Alpine)
RUN apk add --no-cache libc6-compat patchelf

# Set working directory
WORKDIR /app

# Copy the native image from the builder stage
COPY --from=builder /app/build/native/nativeCompile/chzzk_bot .

# Ensure the application binary is executable
RUN chmod +x /app/chzzk_bot

# Patch the binary to use the correct dynamic linker
RUN patchelf --set-interpreter /lib/ld-linux-x86-64.so.2 /app/chzzk_bot

# Run the application
CMD ["./chzzk_bot"]