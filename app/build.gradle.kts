import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlin.serialization)
    id("com.google.protobuf") version "0.9.5" // refers to protobuf-gradle-plugin
}

group = "com.example"
version = "0.0.1"

application {
    mainClass = "io.ktor.server.netty.EngineMain"
}

repositories {
    mavenLocal()
    mavenCentral()
    maven { url = uri("https://packages.confluent.io/maven/") }
}

tasks.register<Copy>("copyFrontend") {
    from(layout.projectDirectory.dir("ServerGui/dist"))
    into(layout.projectDirectory.dir("src/main/resources/static"))
}

tasks.named<ProcessResources>("processResources") {
    dependsOn("copyFrontend")
}

tasks.test {
    useJUnitPlatform()

}

dependencies {
    implementation(libs.geok)
//    implementation(libs.gatas.library)
    implementation(libs.ktor.server.core)
    implementation(libs.koin.ktor)
    implementation(libs.koin.logger.slf4j)
    implementation(libs.ktor.server.rate.limiting)
//    implementation(libs.ktor.server.thymeleaf)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.core)
    implementation(libs.ktor.cio)
    implementation(libs.ktor.logging)
    implementation(libs.ktor.serverContentNegotiation)
    implementation(libs.ktor.clientContentNegotiation)
    implementation(libs.ktor.server.metrics.micrometer)
    implementation(libs.micrometer.registry.prometheus)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.json)
    implementation(libs.ktor.xml)
    implementation(libs.pdvrieze.serialization)
    implementation(libs.ktor.client.encoding.jvm)

    implementation(libs.ktor.network)
//    implementation(libs.logback.classic)
    implementation(libs.kotlinx.coroutines.reactive)
    implementation(libs.kermit)
    implementation(libs.letuce.core)
    implementation(libs.ktor.server.config.yaml)
    implementation(libs.ktor.compression)

    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlin.test.junit)

    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.testcontainers) // Check for latest version
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    testImplementation("io.insert-koin:koin-test:3.5.3")
    // Specifically for JUnit 5 support (provides @JvmField and extension support)
    testImplementation("io.insert-koin:koin-test-junit5:3.5.3")


    implementation(libs.okio)
    implementation(libs.okio.jvm)

    implementation(libs.uber.h3)
}
