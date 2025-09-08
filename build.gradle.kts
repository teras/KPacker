val korrV = "0.1.0"
val serializationV = "1.9.0"

plugins {
    kotlin("multiplatform") version "2.2.10"
    kotlin("plugin.serialization") version "2.2.10" // Use the Kotlin version you're using in the project
    id("com.gradleup.shadow") version "9.1.0" // if Gradle ≥ 8.11; use 8.3.5 on Gradle 8.3–8.10
    `maven-publish`
}

group = "onl.ycode"
version = "0.1.0"

repositories {
    mavenCentral()
    mavenLocal()
}

kotlin {
    applyDefaultHierarchyTemplate()

    jvmToolchain(21)

    jvm {
        @OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)
        binaries {
            executable {
                // entry point for JVM (the file with `fun main()` compiles to MainKt)
                mainClass.set("onl.ycode.jubpak.MainKt")
            }
        }
    }

    val linuxX64 = linuxX64()
    val mingwX64 = mingwX64()

    listOf(linuxX64, mingwX64).forEach {
        it.binaries {
            executable {
                entryPoint = "onl.ycode.jubpak.main"
            }
        }
//        it.compilations["main"].cinterops.create("libcurl")
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("onl.ycode:korr:$korrV")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$serializationV")
                implementation("io.ktor:ktor-client-core:3.0.0")
                implementation("io.ktor:ktor-client-curl:3.0.0")
            }
        }
        val nativeMain by getting

        val posixMain by creating {
            dependsOn(nativeMain)
        }

        val linuxMain by getting {
            dependsOn(posixMain)
        }

        val jvmMain by getting

    }
}


tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    minimizeJar = true
    manifest { attributes["Main-Class"] = "onl.ycode.jubpak.MainKt" }
}