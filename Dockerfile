# Stage 1: Build the executable with GraalVM
FROM ghcr.io/graalvm/native-image-community:21-muslib-ol8 as build

WORKDIR /app

# Copy the Gradle files and source code
COPY build.gradle.kts settings.gradle.kts gradlew ./
COPY gradle gradle
COPY src src

RUN microdnf install findutils

# Build the project
RUN ./gradlew nativeCompile

# Stage 2: Create a minimal Docker image and add the binary
FROM alpine:3.13

WORKDIR /app

# Copy the executable from the build stage
COPY --from=build /app/build/native/nativeCompile/chzzk_bot .

# Set the entry point
ENTRYPOINT ["./chzzk_bot"]