/*
 * SPDX-License-Identifier: BSD-3-Clause
 * Copyright (c) 2025, KPacker Contributors
 */


package onl.ycode.kpacker

import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM
import onl.ycode.kpacker.utils.ImageConverter
import onl.ycode.kpacker.utils.Manifest
import onl.ycode.kpacker.utils.PackageDownloader
import onl.ycode.kpacker.utils.TempFolderRegistry


class Application(private val args: Args) {
    val sourceDir = args.source.toPath().normalized()

    private val foundJars = FileSystem.SYSTEM.list(this.sourceDir).map { it.name }.filter { it.endsWith(".jar") }

    val mainJar = args.mainjar?.also { if (!foundJars.contains(it)) error("Main jar $it not found") }
        ?: if (foundJars.size == 1) foundJars[0] else if (foundJars.isEmpty()) error("Main jar not found") else error("Multiple jars found")

    val otherJars = foundJars.filterNot { it == this.mainJar }

    val name = args.name ?: this.mainJar.substringBeforeLast(".")

    val version = args.version

    val mainClass = Manifest.load(this.sourceDir / this.mainJar)["Main-Class"]
        ?: error("Main-Class not found in manifest of ${this.sourceDir}")

    // Helper properties for easy access to args values
    val icon: String? get() = args.icon
    val p12File: String? get() = args.p12File
    val p12Pass: String? get() = args.p12Pass
    val notaryJson: String? get() = args.notaryJson
    val enableSigning: Boolean get() = args.enableSigning.lowercase() == "true"
    val dmgTemplate: String? get() = args.dmgTemplate
    val dmgCompress: Boolean get() = args.dmgCompress

    // Creating files

    suspend fun getIconPath(): okio.Path? {
        val originalIconPath = findOriginalIconPath()

        if (originalIconPath == null) {
            if (DEBUG) println("No icon found locally, downloading default icon")
            return PackageDownloader.fetchAppIcon()
        }

        // Convert the icon to standardized PNG format if needed
        return convertIconToPng(originalIconPath)
    }

    private suspend fun findOriginalIconPath(): okio.Path? {
        // Only use explicitly provided icon
        val iconValue = icon
        if (iconValue != null) {
            val iconPath = iconValue.toPath()
            if (FileSystem.SYSTEM.exists(iconPath) && ImageConverter.isSupportedImageFormat(iconPath)) {
                if (DEBUG) println("Using provided icon: $iconPath")
                return iconPath
            } else {
                if (DEBUG) println("Provided icon not found or unsupported format: $iconPath")
            }
        }

        return null
    }

    private suspend fun convertIconToPng(originalIconPath: okio.Path): okio.Path? {
        // If it's already a PNG, we still process it to ensure consistent sizing
        val isAlreadyPng = originalIconPath.name.lowercase().endsWith(".png")

        // Create temp directory for the converted icon
        val tempDir = TempFolderRegistry.createTempFolder("icon-", "-convert").toPath()
        val convertedIconName = ImageConverter.getStandardPngName(originalIconPath, name)
        val convertedIconPath = tempDir / convertedIconName

        if (DEBUG) {
            if (isAlreadyPng) {
                println("Processing PNG icon for consistent sizing: ${originalIconPath.name}")
            } else {
                println("Converting ${originalIconPath.name} to standardized PNG format")
            }
        }

        val success = ImageConverter.convertToPng(originalIconPath, convertedIconPath)

        if (success && FileSystem.SYSTEM.exists(convertedIconPath)) {
            if (DEBUG) println("Icon conversion successful: $convertedIconPath")
            return convertedIconPath
        } else {
            if (DEBUG) println("Icon conversion failed, falling back to original")
            // Clean up failed temp directory
            TempFolderRegistry.deleteTempFolder(tempDir.toString())
            // Return original path if conversion failed
            return originalIconPath
        }
    }

    fun createConfig() = StringBuilder().apply {
        appendLine("[Application]")
        appendLine("app.mainclass=$mainClass")
        appendLine($$"app.classpath=$APPDIR/$$mainJar")
        otherJars.forEach { appendLine($$"app.classpath=$APPDIR/$$it") }
        appendLine()
        appendLine("[JavaOptions]")
        appendLine("java-options=-Djpackage.app-version=$version")
    }.toString()
}
