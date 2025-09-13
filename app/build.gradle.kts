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

dependencies {
    implementation(libs.geok)
    implementation(libs.gatas.library)
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

    implementation(libs.protobuf.kotlin)  // com.google.protobuf:protobuf-kotlin
    implementation(libs.grpc.kotlin.stub) // io.grpc:grpc-kotlin-stub
    implementation(libs.grpc.protobuf)    // io.grpc:grpc-protobuf
    implementation(libs.grpc.stub)        // io.grpc:grpc-stub
//    implementation(libs.protobuf.java.util) // com.google.protobuf:protobuf-java-util
    implementation(libs.protobuf.java)    // com.google.protobuf:protobuf-java

    runtimeOnly(libs.grpc.netty.shaded)   // io.grp:grpc-netty-shaded

    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlin.test.junit)

    implementation(libs.okio)
    implementation(libs.okio.jvm)

    implementation(libs.uber.h3)
}

sourceSets {
    val grpc = create("grpckt") {
    }
    getByName("test") {
        compileClasspath += grpc.output
        runtimeClasspath += grpc.output
    }
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.25.8"
    }
    plugins {
        create("grpc") {
            // THis needs Rosetta on ARM to work
            artifact = "io.grpc:protoc-gen-grpc-java:1.73.0"
        }
        create("grpckt") {
            artifact = "io.grpc:protoc-gen-grpc-kotlin:1.4.3:jdk8@jar"
        }
    }


    generateProtoTasks {
        all().forEach {
            it.plugins {
                create("grpc")
                create("grpckt")
            }
            it.builtins {
                create("kotlin")
            }
        }
    }
}
