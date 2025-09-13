import com.vanniktech.maven.publish.SonatypeHost
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val CI = providers.gradleProperty("CI")
    .map(String::toBoolean)
    .getOrElse(false)

val withIOS = false;

plugins {
    id("maven-publish")
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.vanniktech.mavenPublish)
    alias(libs.plugins.kotlin.serialization)
}

group = "nl.rvantwisk.gatas"
version = "1.0.0"

android {
    namespace = "nl.rvantwisk.gatas"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvm()
    linuxX64()

    androidTarget {
        publishLibraryVariants("release")
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    if (withIOS) {
        iosX64()
        iosArm64()
        iosSimulatorArm64()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.bundles.ktor)
                implementation(libs.kermit)
                implementation(libs.koin.core)
            }
        }

        val jvmMain by getting {
            dependencies {
                implementation(libs.ktor.client.encoding.jvm)
                implementation(libs.koin.ktor)
            }
        }

        androidMain.dependencies {
            implementation(libs.ktor.okhttp)

            implementation(libs.koin.android)
            implementation(libs.koin.logger.slf4j)
        }

        if (withIOS) {
            iosMain.dependencies {
                implementation(libs.ktor.darwin)
                implementation(libs.bundles.ktor)
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
    }
}

val keywords = listOf("debug", "ios", "android")
tasks.matching { task ->
    keywords.any { keyword -> task.name.contains(keyword, ignoreCase = true) }
}.configureEach {
    enabled = !CI
}


mavenPublishing {
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)

//    signAllPublications()

    coordinates(group.toString(), "gatas-library", version.toString())

    pom {
        name = "GA/TAS Core Library"
        description = "Kotlin Library for Andoid/iOS/native and JVM to send and receive data from and to GATAS"
        inceptionYear = "2025"
        url = "https://github.com/rvt/openace"
        licenses {
            license {
                name = "XXX"
                url = "YYY"
                distribution = "ZZZ"
            }
        }
        developers {
            developer {
                id = "XXX"
                name = "YYY"
                url = "ZZZ"
            }
        }
        scm {
            url = "XXX"
            connection = "YYY"
            developerConnection = "ZZZ"
        }
    }
}

