/*
 * SPDX-License-Identifier: BSD-3-Clause
 * Copyright (c) 2025, KPacker Contributors
 */

val okioV = "3.16.0"

val korenV = "0.1.0"
val argosV = "1.0.0"
val serializationV = "1.9.0"

// Installation configuration
val installLocation = project.findProperty("installLocation")?.toString()
    ?: "${System.getProperty("user.home")}/Works/System/bin"

plugins {
    kotlin("multiplatform") version "2.2.10"
    kotlin("plugin.serialization") version "2.2.10" // Use the Kotlin version you're using in the project
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

    jvmToolchain(11)

    jvm {
        @OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)
        binaries {
            executable {
                // entry point for JVM (the file with `fun main()` compiles to MainKt)
                mainClass.set("onl.ycode.kpacker.MainKt")
            }
        }
    }

    val linuxX64 = linuxX64()
    val mingwX64 = mingwX64()

    listOf(linuxX64, mingwX64).forEach {
        it.binaries {
            executable {
                entryPoint = "onl.ycode.kpacker.main"
            }
        }
//        it.compilations["main"].cinterops.create("libcurl")
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("onl.ycode:koren:$korenV")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$serializationV")
                implementation("io.ktor:ktor-client-core:3.0.0")
                implementation("com.squareup.okio:okio:${okioV}")
                implementation("onl.ycode:argos:$argosV")
            }
        }
        val nativeMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-curl:3.0.0")
            }
        }

        val posixMain by creating {
            dependsOn(nativeMain)
        }

        val linuxMain by getting {
            dependsOn(posixMain)
        }

        val jvmMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-java:3.0.0")
                implementation("ch.qos.logback:logback-classic:1.5.6")
            }
        }

    }
}


tasks.register<Jar>("fatJar") {
    dependsOn("jvmJar")
    archiveClassifier.set("fat")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest { attributes["Main-Class"] = "onl.ycode.kpacker.MainKt" }

    from(tasks.named("jvmJar").get().outputs.files.map { zipTree(it) })
    from({
        configurations.named("jvmRuntimeClasspath").get().filter { it.name.endsWith(".jar") }.map { zipTree(it) }
    })
}

tasks.register("installNative") {
    group = "installation"
    description = "Compile and install native executables to system bin directory"

    dependsOn("linkReleaseExecutableLinuxX64", "linkReleaseExecutableMingwX64")

    doLast {
        val projectName = project.name.lowercase()
        val archAllDir = File("$installLocation/arch/all")

        // Create directory structure
        archAllDir.mkdirs()

        // Define architecture mappings
        val archMappings = mapOf(
            "linuxX64" to mapOf(
                "archDir" to "linux-x86_64",
                "suffix" to ".linux",
                "extension" to ""
            ),
            "mingwX64" to mapOf(
                "archDir" to "windows-x86_64",
                "suffix" to ".64",
                "extension" to ".exe"
            )
        )

        archMappings.forEach { (target, config) ->
            val archDir = config["archDir"]!!
            val suffix = config["suffix"]!!
            val extension = config["extension"]!!

            // Find the executable file
            val executablePath = when (target) {
                "linuxX64" -> file("build/bin/linuxX64/releaseExecutable/${projectName}.kexe")
                "mingwX64" -> file("build/bin/mingwX64/releaseExecutable/${projectName}.exe")
                else -> throw GradleException("Unknown target: $target")
            }

            if (executablePath.exists()) {
                // Copy to arch/all with proper naming
                val targetFileName = "$projectName$suffix$extension"
                val targetFile = File(archAllDir, targetFileName)

                println("Installing $target executable: ${executablePath.absolutePath} -> ${targetFile.absolutePath}")
                executablePath.copyTo(targetFile, overwrite = true)

                // Strip the executable to reduce size
                val stripCommand = when (target) {
                    "linuxX64" -> "strip"
                    "mingwX64" -> "x86_64-w64-mingw32-strip"
                    else -> null
                }

                if (stripCommand != null) {
                    val stripResult = ProcessBuilder(stripCommand, targetFile.absolutePath)
                        .start()
                        .waitFor()

                    if (stripResult == 0) {
                        println("Stripped executable: ${targetFile.absolutePath}")
                    } else {
                        println("Warning: Failed to strip executable with $stripCommand: ${targetFile.absolutePath}")
                    }
                }

                // Make executable
                targetFile.setExecutable(true, false)

                // Create arch-specific directory and symlink
                val archSpecificDir = File("$installLocation/arch/$archDir")
                archSpecificDir.mkdirs()

                val symlinkName = if (extension.isNotEmpty()) "$projectName$extension" else projectName
                val symlinkFile = File(archSpecificDir, symlinkName)
                val symlinkTarget = "../all/$targetFileName"

                // Remove existing symlink if it exists
                if (symlinkFile.exists()) {
                    symlinkFile.delete()
                }

                // Create symlink using ProcessBuilder
                val symlinkResult = ProcessBuilder("ln", "-s", symlinkTarget, symlinkFile.absolutePath)
                    .start()
                    .waitFor()

                if (symlinkResult == 0) {
                    println("Created symlink: ${symlinkFile.absolutePath} -> $symlinkTarget")
                } else {
                    println("Warning: Failed to create symlink for $archDir")
                }
            } else {
                println("Warning: Executable not found for $target: ${executablePath.absolutePath}")
            }
        }

        println("Native installation completed to: $installLocation")
    }
}
