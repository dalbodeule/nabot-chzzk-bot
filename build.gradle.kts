plugins {
    val kotlinVersion = "2.0.0"

    id("java")
    id("application")
    kotlin("jvm") version kotlinVersion
    kotlin("plugin.jpa") version kotlinVersion
    id("org.graalvm.buildtools.native") version "0.10.2"
}

group = "${project.group}"
version = "${project.version}"

java {
    sourceCompatibility = JavaVersion.VERSION_21
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("${"${project.group}.${project.name}".lowercase()}.MainKt")
}

graalvmNative {
    agent {
        trackReflectionMetadata.set(true)

        metadataCopy {
            outputDirectories.add("src/main/resources/META-INF/native-image")
            mergeWithExisting.set(true)
        }
    }
    binaries {
        binaries.all {
            resources.autodetect()
        }
        named("main") {
            useFatJar.set(true)
            sharedLibrary.set(false)
            buildArgs.add("-march=compatibility")
            buildArgs.add("--enable-http")
            buildArgs.add("--enable-https")
        }
    }
    metadataRepository {
        enabled.set(true)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // https://mvnrepository.com/artifact/net.dv8tion/JDA
    implementation("net.dv8tion:JDA:5.0.0-beta.24") {
        exclude(module = "opus-java")
    }
    // https://mvnrepository.com/artifact/io.github.R2turnTrue/chzzk4j
    implementation("io.github.R2turnTrue:chzzk4j:0.0.7")

    implementation("ch.qos.logback:logback-classic:1.4.14")

    // https://mvnrepository.com/artifact/org.jetbrains.exposed/exposed-core
    implementation("org.jetbrains.exposed:exposed-core:0.51.1")
    // https://mvnrepository.com/artifact/org.jetbrains.exposed/exposed-dao
    implementation("org.jetbrains.exposed:exposed-dao:0.51.1")
    // https://mvnrepository.com/artifact/org.jetbrains.exposed/exposed-jdbc
    implementation("org.jetbrains.exposed:exposed-jdbc:0.51.1")
    // https://mvnrepository.com/artifact/org.jetbrains.exposed/exposed-kotlin-datetime
    implementation("org.jetbrains.exposed:exposed-java-time:0.51.1")

    // https://mvnrepository.com/artifact/com.zaxxer/HikariCP
    implementation("com.zaxxer:HikariCP:5.1.0")

    // https://mvnrepository.com/artifact/org.jetbrains.kotlinx/kotlinx-coroutines-core
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0-RC")
    // https://mvnrepository.com/artifact/org.jetbrains.kotlin/kotlin-reflect
    implementation("org.jetbrains.kotlin:kotlin-reflect:2.0.0")
    // https://mvnrepository.com/artifact/org.reflections/reflections
    implementation("org.reflections:reflections:0.10.2")

    // https://mvnrepository.com/artifact/org.mariadb.jdbc/mariadb-java-client
    implementation("org.mariadb.jdbc:mariadb-java-client:3.4.0")

    implementation("io.github.cdimascio:dotenv-kotlin:6.4.1")

    kotlin("stdlib")
}

tasks.withType<Jar> {
    manifest {
        attributes["Main-Class"] = application.mainClass
    }

    from(sourceSets.main.get().output)

    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) }
    })

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
