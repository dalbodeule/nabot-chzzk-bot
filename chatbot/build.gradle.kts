plugins {
    kotlin("jvm")
}

group = project.rootProject.group
version = project.rootProject.version

repositories {
    mavenCentral()
}

dependencies {
    // https://mvnrepository.com/artifact/net.dv8tion/JDA
    api("net.dv8tion:JDA:5.0.1") {
        exclude(module = "opus-java")
    }

    // https://mvnrepository.com/artifact/io.github.R2turnTrue/chzzk4j
    implementation("io.github.R2turnTrue:chzzk4j:0.0.9")

    // https://mvnrepository.com/artifact/ch.qos.logback/logback-classic
    implementation("ch.qos.logback:logback-classic:1.5.6")

    // https://mvnrepository.com/artifact/org.jetbrains.kotlinx/kotlinx-coroutines-core
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0-RC")
    // https://mvnrepository.com/artifact/org.jetbrains.kotlin/kotlin-reflect
    implementation("org.jetbrains.kotlin:kotlin-reflect:2.0.0")

    // https://mvnrepository.com/artifact/com.google.code.gson/gson
    implementation("com.google.code.gson:gson:2.11.0")

    // https://mvnrepository.com/artifact/io.github.cdimascio/dotenv-kotlin
    implementation("io.github.cdimascio:dotenv-kotlin:6.4.1")

    // https://mvnrepository.com/artifact/com.squareup.okhttp3/okhttp
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // https://mvnrepository.com/artifact/io.insert-koin/koin-core
    implementation("io.insert-koin:koin-core:4.0.0-RC1")

    testImplementation(kotlin("test"))

    listOf(project(":common")).forEach {
        implementation(it)
    }
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}