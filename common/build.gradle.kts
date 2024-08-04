plugins {
    kotlin("jvm")
}

group = project.rootProject.group
version = project.rootProject.version

repositories {
    mavenCentral()
}

dependencies {
    // https://mvnrepository.com/artifact/org.jetbrains.exposed/exposed-core
    api("org.jetbrains.exposed:exposed-core:0.52.0")
    // https://mvnrepository.com/artifact/org.jetbrains.exposed/exposed-dao
    api("org.jetbrains.exposed:exposed-dao:0.52.0")
    // https://mvnrepository.com/artifact/org.jetbrains.exposed/exposed-jdbc
    api("org.jetbrains.exposed:exposed-jdbc:0.52.0")
    // https://mvnrepository.com/artifact/org.jetbrains.exposed/exposed-kotlin-datetime
    api("org.jetbrains.exposed:exposed-java-time:0.52.0")

    // https://mvnrepository.com/artifact/com.zaxxer/HikariCP
    api("com.zaxxer:HikariCP:5.1.0")

    // https://mvnrepository.com/artifact/ch.qos.logback/logback-classic
    implementation("ch.qos.logback:logback-classic:1.5.6")

    // https://mvnrepository.com/artifact/org.mariadb.jdbc/mariadb-java-client
    implementation("org.mariadb.jdbc:mariadb-java-client:3.4.1")

    // https://mvnrepository.com/artifact/io.github.cdimascio/dotenv-kotlin
    implementation("io.github.cdimascio:dotenv-kotlin:6.4.1")

    // https://mvnrepository.com/artifact/com.squareup.okhttp3/okhttp
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // https://mvnrepository.com/artifact/com.google.code.gson/gson
    implementation("com.google.code.gson:gson:2.11.0")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}
