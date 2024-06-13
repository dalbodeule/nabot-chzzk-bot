# Stage 1: Build the executable with GraalVM
FROM gradle:jdk-21-and-22-graal as build

WORKDIR /app

# Copy the Gradle files and source code
COPY build.gradle.kts settings.gradle.kts gradlew gradle.properties ./
COPY gradle gradle
COPY src src

# Build the project
RUN gradle nativeCompile

# Stage 2: Create a minimal Docker image and add the binary
FROM alpine:3.13

WORKDIR /app

# https://stackoverflow.com/a/77779723/11516704
RUN apk add gcompat

# Copy the executable from the build stage
COPY --from=build /app/build/native/nativeCompile/chzzk_bot .
COPY --from=build /app/build/native/nativeCompile/libjsound.so .

# Set the entry point
ENTRYPOINT ["./chzzk_bot"]