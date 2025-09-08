/*
 * SPDX-License-Identifier: BSD-3-Clause
 * Copyright (c) 2025, KPacker Contributors
 */


package onl.ycode.kpacker.packers

import okio.ByteString
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM
import onl.ycode.koren.Command
import onl.ycode.kpacker.Application
import onl.ycode.kpacker.DEBUG
import onl.ycode.kpacker.utils.ContainerFactory
import onl.ycode.kpacker.utils.FileUtils
import onl.ycode.kpacker.utils.PackageDownloader
import onl.ycode.kpacker.utils.TempFolderRegistry

object MacX64Configurator : Configurator {
    override suspend fun fetchDist() = PackageDownloader.fetchMacX64()
    override fun baseDirName(name: String) = "$name.app"
    override fun binLocation(name: String) = "Contents/MacOS/$name"
    override val appLocation = "Contents/app"

    override suspend fun postProcess(targetDir: String, app: Application) {
        updateMacOSTemplateFiles(targetDir, app)
    }

    private suspend fun updateMacOSTemplateFiles(outputDir: String, app: Application) {
        val fs = FileSystem.SYSTEM
        val outputDirPath = outputDir.toPath()
        val appBundleDir = outputDirPath / "${app.name}.app"
        val contentsDir = appBundleDir / "Contents"

        if (DEBUG) println("Updating macOS template files for ${app.name}")

        // Update Info.plist with app-specific information
        updateInfoPlist(contentsDir, app)

        // Update .jpackage.xml with app-specific information
        updateJPackageXML(contentsDir / "app", app)

        // Handle icon conversion and placement
        val resourcesDir = contentsDir / "Resources"
        handleIcon(resourcesDir, app)

        // Create DMG file for macOS distribution
        createDMG(outputDir, app)

        // Sign app bundle and DMG if signing is enabled
        if (app.enableSigning) {
            signMacOSApp(appBundleDir, app)
            val dmgPath = outputDirPath / "${app.name}-${app.version}.dmg"
            if (fs.exists(dmgPath)) {
                signMacOSApp(dmgPath, app)
                notarizeMacOSApp(dmgPath, app)
            }
        }

        if (DEBUG) println("macOS app bundle and DMG created for ${app.name}")
    }

    private suspend fun updateInfoPlist(contentsDir: okio.Path, app: Application) {
        val fs = FileSystem.SYSTEM
        val infoPlistPath = contentsDir / "Info.plist"

        if (fs.exists(infoPlistPath)) {
            // Read existing Info.plist
            var infoPlistContent = fs.read(infoPlistPath) { readUtf8() }

            // Update key values using more specific patterns
            infoPlistContent = infoPlistContent
                // Update CFBundleExecutable
                .replace(Regex("<key>CFBundleExecutable</key>\\s*<string>.*?</string>")) {
                    "<key>CFBundleExecutable</key>\n  <string>${app.name}</string>"
                }
                // Update CFBundleName
                .replace(Regex("<key>CFBundleName</key>\\s*<string>.*?</string>")) {
                    "<key>CFBundleName</key>\n  <string>${app.name}</string>"
                }
                // Update CFBundleIdentifier
                .replace(Regex("<key>CFBundleIdentifier</key>\\s*<string>.*?</string>")) {
                    "<key>CFBundleIdentifier</key>\n  <string>com.${app.name.lowercase()}.${app.name}</string>"
                }
                // Update CFBundleIconFile
                .replace(Regex("<key>CFBundleIconFile</key>\\s*<string>.*?</string>")) {
                    "<key>CFBundleIconFile</key>\n  <string>${app.name}.icns</string>"
                }
                // Update CFBundleShortVersionString
                .replace(Regex("<key>CFBundleShortVersionString</key>\\s*<string>.*?</string>")) {
                    "<key>CFBundleShortVersionString</key>\n  <string>${app.version}</string>"
                }
                // Update CFBundleVersion
                .replace(Regex("<key>CFBundleVersion</key>\\s*<string>.*?</string>")) {
                    "<key>CFBundleVersion</key>\n  <string>${app.version}</string>"
                }

            fs.write(infoPlistPath) { writeUtf8(infoPlistContent) }
            if (DEBUG) println("Updated Info.plist")
        } else {
            if (DEBUG) println("Info.plist not found in template, creating new one")
            createNewInfoPlist(contentsDir, app)
        }
    }

    private suspend fun createNewInfoPlist(contentsDir: okio.Path, app: Application) {
        val fs = FileSystem.SYSTEM
        val infoPlist = buildString {
            appendLine("<?xml version=\"1.0\" ?>")
            appendLine("<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" \"https://www.apple.com/DTDs/PropertyList-1.0.dtd\">")
            appendLine("<plist version=\"1.0\">")
            appendLine(" <dict>")
            appendLine("  <key>LSMinimumSystemVersion</key>")
            appendLine("  <string>10.9</string>")
            appendLine("  <key>CFBundleDevelopmentRegion</key>")
            appendLine("  <string>English</string>")
            appendLine("  <key>CFBundleAllowMixedLocalizations</key>")
            appendLine("  <true/>")
            appendLine("  <key>CFBundleExecutable</key>")
            appendLine("  <string>${app.name}</string>")
            appendLine("  <key>CFBundleIconFile</key>")
            appendLine("  <string>${app.name}.icns</string>")
            appendLine("  <key>CFBundleIdentifier</key>")
            appendLine("  <string>com.${app.name.lowercase()}.${app.name}</string>")
            appendLine("  <key>CFBundleInfoDictionaryVersion</key>")
            appendLine("  <string>6.0</string>")
            appendLine("  <key>CFBundleName</key>")
            appendLine("  <string>${app.name}</string>")
            appendLine("  <key>CFBundlePackageType</key>")
            appendLine("  <string>APPL</string>")
            appendLine("  <key>CFBundleShortVersionString</key>")
            appendLine("  <string>${app.version}</string>")
            appendLine("  <key>CFBundleSignature</key>")
            appendLine("  <string>????</string>")
            appendLine("  <key>LSApplicationCategoryType</key>")
            appendLine("  <string>Unknown</string>")
            appendLine("  <key>CFBundleVersion</key>")
            appendLine("  <string>${app.version}</string>")
            appendLine("  <key>NSHumanReadableCopyright</key>")
            appendLine("  <string>Copyright © 2025</string>")
            appendLine("  <key>NSHighResolutionCapable</key>")
            appendLine("  <string>true</string>")
            appendLine(" </dict>")
            appendLine("</plist>")
        }

        fs.write(contentsDir / "Info.plist") { writeUtf8(infoPlist) }
        if (DEBUG) println("Created new Info.plist")
    }


    private suspend fun handleIcon(resourcesDir: okio.Path, app: Application) {
        val fs = FileSystem.SYSTEM

        // Get standardized PNG icon from the new icon processing system
        val iconPath = app.getIconPath()

        if (iconPath != null && fs.exists(iconPath)) {
            val targetIconPath = resourcesDir / "${app.name}.icns"

            // The icon is now always a standardized 512x512 PNG, so we need to convert to ICNS
            val runner = ContainerFactory.createRunner()
            if (DEBUG) println("Converting standardized PNG to ICNS for macOS")
            // Create temporary working directory
            try {
                // Copy input file to working directory
                fs.copy(iconPath, runner.workingDir / iconPath.name)

                val iconconvertCommand = runner.run("iconconvert ${iconPath.name} ${targetIconPath.name}")
                val exitCode = iconconvertCommand.exec().waitFor()
                if (exitCode != 0) {
                    throw RuntimeException("Failed to convert icon to ICNS format")
                }

                // Copy result back from working directory
                val resultFile = runner.workingDir / targetIconPath.name
                if (fs.exists(resultFile)) {
                    fs.copy(resultFile, targetIconPath)
                    if (DEBUG) println("Successfully converted standardized PNG to .icns")
                } else {
                    throw RuntimeException("ICNS conversion result file not found")
                }
            } finally {
                // Clean up working directory
                try {
                    fs.deleteRecursively(runner.workingDir)
                } catch (e: Exception) {
                    if (DEBUG) println("Failed to clean up ICNS working directory: ${e.message}")
                }
            }
        } else {
            if (DEBUG) println("No icon found, skipping icon creation")
        }
    }

    private suspend fun updateJPackageXML(appDir: okio.Path, app: Application) {
        val fs = FileSystem.SYSTEM
        val jpackageXMLPath = appDir / ".jpackage.xml"

        if (fs.exists(jpackageXMLPath)) {
            // Read existing .jpackage.xml
            var jpackageContent = fs.read(jpackageXMLPath) { readUtf8() }

            // Update key values
            jpackageContent = jpackageContent
                .replace("<app-version>.*?</app-version>".toRegex(), "<app-version>${app.version}</app-version>")
                .replace("<main-launcher>.*?</main-launcher>".toRegex(), "<main-launcher>${app.name}</main-launcher>")

            fs.write(jpackageXMLPath) { writeUtf8(jpackageContent) }
            if (DEBUG) println("Updated .jpackage.xml")
        } else {
            // Create new .jpackage.xml if it doesn't exist
            val jpackageXML = buildString {
                appendLine("<?xml version=\"1.0\" ?>")
                appendLine("<jpackage-state version=\"15.0.1\" platform=\"macOS\">")
                appendLine("  <app-version>${app.version}</app-version>")
                appendLine("  <main-launcher>${app.name}</main-launcher>")
                appendLine("</jpackage-state>")
            }

            fs.write(jpackageXMLPath) { writeUtf8(jpackageXML) }
            if (DEBUG) println("Created new .jpackage.xml")
        }
    }

    private suspend fun createDMG(outputDir: String, app: Application) {
        val fs = FileSystem.SYSTEM
        val outputDirPath = outputDir.toPath()
        val appBundlePath = outputDirPath / "${app.name}.app"
        val dmgPath = outputDirPath / "${app.name}-${app.version}.dmg"

        if (DEBUG) println("Creating DMG for ${app.name}")

        // Check if user provided a DMG template
        val dmgTemplate = app.dmgTemplate
        if (dmgTemplate != null) {
            try {
                if (DEBUG) println("Attempting to use DMG template (experimental feature)")
                createDMGFromTemplate(outputDir, app, appBundlePath, dmgPath, dmgTemplate)
            } catch (e: Exception) {
                if (DEBUG) println("DMG template failed: ${e.message}, falling back to basic DMG")
                createDMGFromScratch(outputDir, app, appBundlePath, dmgPath)
            }
        } else {
            createDMGFromScratch(outputDir, app, appBundlePath, dmgPath)
        }
    }

    private suspend fun createDMGFromTemplate(
        outputDir: String,
        app: Application,
        appBundlePath: okio.Path,
        dmgPath: okio.Path,
        templatePath: String
    ) {
        val fs = FileSystem.SYSTEM
        val templatePath = templatePath.toPath()

        if (!fs.exists(templatePath)) {
            throw RuntimeException("DMG template not found: $templatePath")
        }

        if (DEBUG) println("Using DMG template: $templatePath")

        // Create temporary directory for template extraction
        val templateExtractDir = TempFolderRegistry.createTempFolder("dmg-template-", "-extract").toPath()
        val uncompressedDmgPath = dmgPath.parent!! / "${app.name}-${app.version}-uncompressed.dmg"

        try {
            // Handle template based on type - like Nim approach
            if (templatePath.name.lowercase().endsWith(".zip")) {
                // Extract ZIP to find the DMG inside (like Nim's dmg_template.unzip(datafiles))
                if (DEBUG) println("Template is ZIP file, extracting to find template DMG...")
                val zipExtractDir = TempFolderRegistry.createTempFolder("dmg-zip-", "-extract").toPath()
                try {
                    val unzipCommand = Command("unzip", "-q", templatePath.toString(), "-d", zipExtractDir.toString())
                    val exitCode = unzipCommand.exec().waitFor()
                    if (exitCode != 0) throw RuntimeException("Failed to extract ZIP template")

                    // Find the DMG file inside the extracted ZIP
                    val dmgFiles = fs.list(zipExtractDir).filter { it.name.lowercase().endsWith(".dmg") }
                    if (dmgFiles.isEmpty()) {
                        throw RuntimeException("No DMG file found in ZIP template")
                    }
                    val templateDmgPath = dmgFiles.first()
                    if (DEBUG) println("Found template DMG in ZIP: ${templateDmgPath.name}")

                    // Extract the template DMG contents to the template directory
                    extractDMGContents(templateDmgPath, templateExtractDir)
                } finally {
                    // Cleanup ZIP extraction directory
                    TempFolderRegistry.deleteTempFolder(zipExtractDir.toString())
                }

            } else if (templatePath.name.lowercase().endsWith(".dmg")) {
                // Direct DMG file - extract its contents
                if (DEBUG) println("Template is direct DMG file, extracting contents...")
                extractDMGContents(templatePath, templateExtractDir)
            } else {
                throw RuntimeException("Template must be either .zip or .dmg file")
            }

            // Create uncompressed DMG (200MB should be enough for most apps)
            val truncateCommand = Command("truncate", "-s", "200M", uncompressedDmgPath.toString())
            var exitCode = truncateCommand.exec().waitFor()
            if (exitCode != 0) throw RuntimeException("Failed to create empty DMG file")

            // Format as HFS+ filesystem using container
            val mkfsCommand = Command(
                "docker",
                "run",
                "--rm",
                "-t",
                "-v",
                "${uncompressedDmgPath.parent}:/work",
                "docker.io/teras/appimage-builder",
                "sh",
                "-c",
                "mkfs.hfsplus -v ${app.name} /work/${uncompressedDmgPath.name}"
            )
            exitCode = mkfsCommand.exec().waitFor()
            if (exitCode != 0) throw RuntimeException("Failed to format DMG as HFS+")

            // Mount the new DMG
            val mountOutput = StringBuilder()
            val mountCommand = Command("udisksctl", "loop-setup", "-f", uncompressedDmgPath.toString())
            mountCommand.addOutListener { output ->
                mountOutput.append(output.decodeToString())
            }
            exitCode = mountCommand.exec().waitFor()
            if (exitCode != 0) throw RuntimeException("Failed to mount DMG")

            // Extract loop device path
            val loopDevice = extractLoopDevice(mountOutput.toString())
                ?: throw RuntimeException("Could not determine loop device")

            // Mount the filesystem
            val mountFsOutput = StringBuilder()
            val mountFsCommand = Command("udisksctl", "mount", "-b", loopDevice)
            mountFsCommand.addOutListener { output ->
                mountFsOutput.append(output.decodeToString())
            }
            exitCode = mountFsCommand.exec().waitFor()
            if (exitCode != 0) throw RuntimeException("Failed to mount DMG filesystem")

            // Extract mount point
            val mountPoint = extractMountPoint(mountFsOutput.toString())
                ?: throw RuntimeException("Could not determine mount point")

            val mountPointPath = mountPoint.toPath()

            try {
                // Copy template contents to DMG (like merge mountdir, datafiles in Nim)
                if (DEBUG) println("Copying template data from: $templateExtractDir")
                fs.list(templateExtractDir).forEach { item ->
                    val fileName = item.name

                    if (DEBUG) println("Copying template item: $fileName")
                    try {
                        if (fs.metadata(item).isDirectory) {
                            FileUtils.copyDirectory(fs, item, mountPointPath / item.name)
                        } else {
                            FileUtils.safeCopy(fs, item, mountPointPath / item.name)
                        }
                    } catch (e: Exception) {
                        if (DEBUG) println("Warning: Could not copy template item ${fileName}: ${e.message}")
                    }
                }

                // Find and remove existing .app directory from template
                val existingApps =
                    fs.list(mountPointPath).filter { it.name.endsWith(".app") && fs.metadata(it).isDirectory }
                existingApps.forEach { oldApp ->
                    if (DEBUG) println("Removing template app: ${oldApp.name}")
                    fs.deleteRecursively(oldApp)
                }

                // Copy our new app bundle (like merge appDirName, app in Nim)
                FileUtils.copyDirectory(fs, appBundlePath, mountPointPath / "${app.name}.app")

                // Recreate symlink to /Applications (like Nim does)
                val applicationsLink = mountPointPath / "Applications"
                if (fs.exists(applicationsLink)) {
                    fs.delete(applicationsLink)
                }
                val createLinkCommand = Command("ln", "-s", "/Applications", applicationsLink.toString())
                createLinkCommand.exec().waitFor()
                if (DEBUG) println("Recreated Applications symlink")

                if (DEBUG) println("Merged template contents and replaced app bundle")

            } finally {
                // Unmount filesystem
                val unmountFsCommand = Command("udisksctl", "unmount", "-b", loopDevice)
                unmountFsCommand.exec().waitFor()

                // Detach loop device
                val detachCommand = Command("udisksctl", "loop-delete", "-b", loopDevice)
                detachCommand.exec().waitFor()
            }

            // Compress DMG using container (like Nim's podman dmg command)
            val containerCommand = Command(
                "docker",
                "run",
                "--rm",
                "-t",
                "-v",
                "$outputDir:/data",
                "docker.io/teras/appimage-builder",
                "sh",
                "-c",
                "dmg /data/${app.name}-${app.version}-uncompressed.dmg /data/${app.name}-${app.version}.dmg"
            )
            exitCode = containerCommand.exec().waitFor()
            if (exitCode != 0) throw RuntimeException("Failed to compress DMG")

            // Clean up uncompressed DMG
            if (fs.exists(uncompressedDmgPath)) {
                fs.delete(uncompressedDmgPath)
            }

            if (DEBUG) println("DMG created successfully from template: $dmgPath")

        } finally {
            // Clean up temporary uncompressed DMG
            if (fs.exists(uncompressedDmgPath)) {
                fs.delete(uncompressedDmgPath)
            }
            // Cleanup template extraction directory
            TempFolderRegistry.deleteTempFolder(templateExtractDir.toString())
        }
    }

    private suspend fun extractDMGContents(dmgPath: okio.Path, extractDir: okio.Path) {
        val fs = FileSystem.SYSTEM

        // Mount DMG directly using udisksctl
        if (DEBUG) println("Setting up loop device for: $dmgPath")
        val mountOutput = StringBuilder()
        val mountCommand = Command("udisksctl", "loop-setup", "-f", dmgPath.toString())
        mountCommand.addOutListener { output ->
            mountOutput.append(output.decodeToString())
            if (DEBUG) print("Loop setup: ${output.decodeToString()}")
        }
        mountCommand.addErrorListener { error ->
            if (DEBUG) print("Loop error: ${error.decodeToString()}")
        }
        var exitCode = mountCommand.exec().waitFor()
        if (exitCode != 0) throw RuntimeException("Failed to setup loop device for template DMG (exit: $exitCode)")

        val loopDevice = extractLoopDevice(mountOutput.toString())
            ?: throw RuntimeException("Could not determine loop device for template")

        // Check for partitions on the loop device using lsblk
        val lsblkOutput = StringBuilder()
        val lsblkCommand = Command("lsblk", "-n", "-o", "NAME", loopDevice)
        lsblkCommand.addOutListener { output ->
            lsblkOutput.append(output.decodeToString())
        }
        lsblkCommand.exec().waitFor()

        // Find the right device to mount (check if there's a partition)
        val deviceToMount = if (lsblkOutput.toString().contains("${loopDevice.removePrefix("/dev/")}p1")) {
            "${loopDevice}p1"
        } else {
            loopDevice
        }

        if (DEBUG) println("Mounting device: $deviceToMount")

        val mountFsOutput = StringBuilder()
        val mountFsCommand = Command("udisksctl", "mount", "-b", deviceToMount)
        mountFsCommand.addOutListener { output ->
            mountFsOutput.append(output.decodeToString())
            if (DEBUG) print("Mount FS: ${output.decodeToString()}")
        }
        mountFsCommand.addErrorListener { error ->
            if (DEBUG) print("Mount FS error: ${error.decodeToString()}")
        }
        exitCode = mountFsCommand.exec().waitFor()
        if (exitCode != 0) throw RuntimeException("Failed to mount template DMG filesystem (exit: $exitCode)")

        val mountPoint = extractMountPoint(mountFsOutput.toString())
            ?: throw RuntimeException("Could not determine mount point for template")

        val mountPointPath = mountPoint.toPath()

        try {
            // Copy ALL contents from mounted DMG to extract directory (including hidden files and metadata)
            fs.list(mountPointPath).forEach { item ->
                val fileName = item.name
                // Only skip the problematic filesystem journal files that can't be copied
                if (fileName.startsWith(".journal") || fileName == ".journal_info_block") {
                    if (DEBUG) println("Skipping uncopyable system file: $fileName")
                    return@forEach
                }

                try {
                    if (DEBUG) println("Extracting: ${item.name}")
                    if (fs.metadata(item).isDirectory) {
                        FileUtils.copyDirectory(fs, item, extractDir / item.name)
                    } else {
                        FileUtils.safeCopy(fs, item, extractDir / item.name)
                    }
                } catch (e: Exception) {
                    if (DEBUG) println("Warning: Could not extract ${item.name}: ${e.message}")
                }
            }

            if (DEBUG) println("Extracted template DMG contents successfully")

        } finally {
            // Unmount template DMG
            val unmountFsCommand = Command("udisksctl", "unmount", "-b", deviceToMount)
            unmountFsCommand.exec().waitFor()

            val detachCommand = Command("udisksctl", "loop-delete", "-b", loopDevice)
            detachCommand.exec().waitFor()
        }
    }

    private suspend fun createDMGFromScratch(
        outputDir: String,
        app: Application,
        appBundlePath: okio.Path,
        dmgPath: okio.Path
    ) {
        val fs = FileSystem.SYSTEM
        val uncompressedDmgPath = dmgPath.parent!! / "${app.name}-${app.version}-uncompressed.dmg"

        try {
            // Create uncompressed DMG (200MB should be enough for most apps)
            val truncateCommand = Command("truncate", "-s", "200M", uncompressedDmgPath.toString())
            var exitCode = truncateCommand.exec().waitFor()
            if (exitCode != 0) throw RuntimeException("Failed to create empty DMG file")

            // Format as HFS+ filesystem using container (like makeapp does)
            val mkfsCommand = Command(
                "docker",
                "run",
                "--rm",
                "-t",
                "-v",
                "${uncompressedDmgPath.parent}:/work",
                "docker.io/teras/appimage-builder",
                "sh",
                "-c",
                "mkfs.hfsplus -v ${app.name} /work/${uncompressedDmgPath.name}"
            )
            exitCode = mkfsCommand.exec().waitFor()
            if (exitCode != 0) throw RuntimeException("Failed to format DMG as HFS+")

            // Mount the DMG
            val mountOutput = StringBuilder()
            val mountCommand = Command("udisksctl", "loop-setup", "-f", uncompressedDmgPath.toString())
            mountCommand.addOutListener { output ->
                mountOutput.append(output.decodeToString())
            }
            val mountResult = mountCommand.exec()
            exitCode = mountResult.waitFor()
            if (exitCode != 0) throw RuntimeException("Failed to mount DMG")

            // Extract loop device path from mount output
            val loopDevice = extractLoopDevice(mountOutput.toString())
                ?: throw RuntimeException("Could not determine loop device")

            // Mount the filesystem
            val mountFsOutput = StringBuilder()
            val mountFsCommand = Command("udisksctl", "mount", "-b", loopDevice)
            mountFsCommand.addOutListener { output ->
                mountFsOutput.append(output.decodeToString())
            }
            val mountFsResult = mountFsCommand.exec()
            exitCode = mountFsResult.waitFor()
            if (exitCode != 0) throw RuntimeException("Failed to mount DMG filesystem")

            // Extract mount point from output
            val mountPoint = extractMountPoint(mountFsOutput.toString())
            if (mountPoint == null) throw RuntimeException("Could not determine mount point")

            try {
                val mountPointPath = mountPoint.toPath()

                // Copy app bundle to DMG
                FileUtils.copyDirectory(fs, appBundlePath, mountPointPath / "${app.name}.app")

                // Create Applications symlink
                val applicationsCommand =
                    Command("ln", "-s", "/Applications", (mountPointPath / "Applications").toString())
                applicationsCommand.exec().waitFor()

                if (DEBUG) println("Copied app bundle and created Applications symlink")

            } finally {
                // Unmount filesystem
                val unmountFsCommand = Command("udisksctl", "unmount", "-b", loopDevice)
                unmountFsCommand.exec().waitFor()

                // Detach loop device
                val detachCommand = Command("udisksctl", "loop-delete", "-b", loopDevice)
                detachCommand.exec().waitFor()
            }

            // Compress DMG using container (following makeapp approach)
            val containerCommand = Command(
                "docker",
                "run",
                "--rm",
                "-t",
                "-v",
                "$outputDir:/data",
                "docker.io/teras/appimage-builder",
                "sh",
                "-c",
                "dmg /data/${app.name}-${app.version}-uncompressed.dmg /data/${app.name}-${app.version}.dmg"
            )
            exitCode = containerCommand.exec().waitFor()
            if (exitCode != 0) throw RuntimeException("Failed to compress DMG")

            // Clean up uncompressed DMG
            if (fs.exists(uncompressedDmgPath)) {
                fs.delete(uncompressedDmgPath)
            }

            if (DEBUG) println("DMG created successfully: ${dmgPath}")

        } catch (e: Exception) {
            if (DEBUG) println("DMG creation failed: ${e.message}")
            // Clean up any partial files
            if (fs.exists(uncompressedDmgPath)) fs.delete(uncompressedDmgPath)
            throw e
        }
    }

    private suspend fun createZIP(outputDir: String, app: Application) {
        val fs = FileSystem.SYSTEM
        val outputDirPath = outputDir.toPath()
        val appBundlePath = outputDirPath / "${app.name}.app"
        val zipPath = outputDirPath / "${app.name}-${app.version}.zip"

        if (DEBUG) println("Creating ZIP for ${app.name}")

        try {
            // Create ZIP file containing the app bundle - use absolute paths
            val zipCommand = Command("zip", "-r", zipPath.toString(), appBundlePath.toString())

            if (DEBUG) println("ZIP command: zip -r ${zipPath} ${appBundlePath}")

            val outputBuffer = StringBuilder()
            val errorBuffer = StringBuilder()

            zipCommand.addOutListener { output ->
                outputBuffer.append(output.decodeToString())
            }
            zipCommand.addErrorListener { error ->
                errorBuffer.append(error.decodeToString())
            }

            val exitCode = zipCommand.exec().waitFor()
            if (exitCode != 0) {
                if (DEBUG) {
                    println("ZIP failed with exit code: $exitCode")
                    if (outputBuffer.isNotEmpty()) println("Output: ${outputBuffer.toString().trim()}")
                    if (errorBuffer.isNotEmpty()) println("Error: ${errorBuffer.toString().trim()}")
                }
                throw RuntimeException("Failed to create ZIP file")
            }

            if (DEBUG) println("ZIP created successfully: ${zipPath}")

        } catch (e: Exception) {
            if (DEBUG) println("ZIP creation failed: ${e.message}")
            // Clean up any partial files
            if (fs.exists(zipPath)) fs.delete(zipPath)
            throw e
        }
    }

    private fun extractLoopDevice(output: String): String? {
        // Parse output like "Mapped file /path/to/file as /dev/loop0."
        val regex = Regex("Mapped file .* as (/dev/loop\\d+)")
        return regex.find(output)?.groupValues?.get(1)
    }

    private fun extractMountPoint(output: String): String? {
        // Parse output like "Mounted /dev/loop0 at /run/media/user/volume"
        val regex = Regex("Mounted .* at (.*)$")
        return regex.find(output.trim())?.groupValues?.get(1)
    }

    private suspend fun signMacOSApp(path: okio.Path, app: Application) {
        val p12File = app.p12File
        val p12Pass = app.p12Pass
        if (p12File == null || p12Pass == null) {
            if (DEBUG) println("Warning: Missing p12 file or password, skipping signing")
            return
        }

        val fs = FileSystem.SYSTEM
        if (!fs.exists(p12File.toPath())) {
            if (DEBUG) println("Warning: P12 file not found: $p12File")
            return
        }
        if (!fs.exists(p12Pass.toPath())) {
            if (DEBUG) println("Warning: P12 password file not found: $p12Pass")
            return
        }

        val isAppBundle = fs.metadata(path).isDirectory && path.name.endsWith(".app")
        val isDMG = path.name.endsWith(".dmg")
        val isZIP = path.name.endsWith(".zip")

        if (isAppBundle) {
            if (DEBUG) println("Deep signing app bundle: ${path.name}")
            deepSignAppBundle(path, app)
        } else if (isDMG) {
            if (DEBUG) println("Signing DMG: ${path.name}")
            signSingleItem(path, app, false)
        } else if (isZIP) {
            if (DEBUG) println("Skipping ZIP signing (ZIP files don't need signing, just notarization)")
            // ZIP files don't need to be signed themselves, just their contents (which we already signed)
        } else {
            if (DEBUG) println("Signing file: ${path.name}")
            signSingleItem(path, app, false)
        }
    }

    private suspend fun deepSignAppBundle(appBundlePath: okio.Path, app: Application) {
        val fs = FileSystem.SYSTEM

        // First, find all signable binaries inside the app bundle filesystem
        val signableBinaries = mutableListOf<okio.Path>()
        findSignableBinaries(appBundlePath, signableBinaries)

        if (DEBUG) println("Found ${signableBinaries.size} signable binaries in app bundle")

        // Skip JAR binary processing - it's not working properly and may not be necessary
        if (DEBUG) println("Skipping JAR binary processing (focus on filesystem binaries for notarization)")

        // Sign each binary individually (libraries don't get runtime flag)
        for (binary in signableBinaries) {
            val isExecutable = isExecutableFile(binary)
            if (DEBUG) println("Signing ${if (isExecutable) "executable" else "library"}: ${binary.name}")
            signSingleItem(binary, app, isExecutable)
        }

        // Finally, sign the app bundle itself
        if (DEBUG) println("Signing app bundle: ${appBundlePath.name}")
        signSingleItem(appBundlePath, app, true)
    }

    private suspend fun findSignableBinaries(dir: okio.Path, binaries: MutableList<okio.Path>) {
        val fs = FileSystem.SYSTEM

        try {
            fs.list(dir).forEach { child ->
                if (fs.metadata(child).isDirectory) {
                    // Recursively search subdirectories
                    findSignableBinaries(child, binaries)
                } else {
                    // Check if this is a signable binary
                    if (isSignableBinary(child)) {
                        binaries.add(child)
                    }
                }
            }
        } catch (e: Exception) {
            if (DEBUG) println("Warning: Could not list directory $dir: ${e.message}")
        }
    }

    private fun isSignableBinary(path: okio.Path): Boolean {
        val name = path.name.lowercase()
        return when {
            // Native libraries
            name.endsWith(".dylib") -> true
            name.endsWith(".jnilib") -> true
            name.endsWith(".so") -> true
            // Executables (check if they're actually executable files)
            !name.contains(".") && isExecutableFile(path) -> true
            // Frameworks
            name.endsWith(".framework") -> true
            else -> false
        }
    }

    private fun isExecutableFile(path: okio.Path): Boolean {
        val fs = FileSystem.SYSTEM
        return try {
            // Check if file exists and try to read first few bytes to detect binary
            if (!fs.exists(path)) return false
            val metadata = fs.metadata(path)
            if (metadata.isDirectory) return false

            // Read first 4 bytes to check for Mach-O magic numbers
            val fileSize = metadata.size ?: 0L
            val header = fs.read(path) {
                if (exhausted()) return@read ByteString.EMPTY
                readByteString(minOf(4L, fileSize))
            }

            if (header.size >= 4) {
                val magic = header.toByteArray()
                // Mach-O magic numbers (little-endian): 0xcffaedfe (64-bit), 0xcefaedfe (32-bit), 0xcafebabe (universal)
                return ((magic[0] == 0xcf.toByte() && magic[1] == 0xfa.toByte() && magic[2] == 0xed.toByte() && magic[3] == 0xfe.toByte()) ||
                        (magic[0] == 0xce.toByte() && magic[1] == 0xfa.toByte() && magic[2] == 0xed.toByte() && magic[3] == 0xfe.toByte()) ||
                        (magic[0] == 0xca.toByte() && magic[1] == 0xfe.toByte() && magic[2] == 0xba.toByte() && magic[3] == 0xbe.toByte()))
            }

            false
        } catch (e: Exception) {
            if (DEBUG) println("Warning: Could not check if $path is executable: ${e.message}")
            false
        }
    }

    private suspend fun signJarBinaries(appDir: okio.Path, app: Application) {
        val fs = FileSystem.SYSTEM
        var totalJarBinaries = 0

        try {
            // Find all JAR files in the app directory
            fs.list(appDir).forEach { jarPath ->
                if (jarPath.name.endsWith(".jar")) {
                    val jarBinaries = processJarFile(jarPath, app)
                    if (jarBinaries > 0) {
                        totalJarBinaries += jarBinaries
                    }
                }
            }

            if (DEBUG && totalJarBinaries > 0) {
                println("Signed $totalJarBinaries binaries inside JAR files")
            }
        } catch (e: Exception) {
            if (DEBUG) println("Warning: Error processing JAR files: ${e.message}")
        }
    }

    private suspend fun processJarFile(jarPath: okio.Path, app: Application): Int {
        val fs = FileSystem.SYSTEM
        var signedCount = 0

        try {
            // Create temporary directory for extraction
            val tempDir = jarPath.parent!! / ".temp_jar_${jarPath.name}_${kotlin.random.Random.nextInt(10000)}"
            fs.createDirectories(tempDir)

            try {
                // First, list contents of JAR to find signable binaries
                val listCommand = Command("jar", "-tf", jarPath.toString())
                val jarContents = StringBuilder()
                listCommand.addOutListener { output ->
                    jarContents.append(output.decodeToString())
                }

                val listExitCode = listCommand.exec().waitFor()
                if (listExitCode != 0) {
                    if (DEBUG) println("Warning: Could not list JAR contents: ${jarPath.name}")
                    return 0
                }

                // Find binary entries
                val binaryEntries = jarContents.toString().lines()
                    .filter { entry ->
                        entry.isNotBlank() &&
                                !entry.endsWith("/") &&
                                isSignableBinaryPath(entry)
                    }

                if (binaryEntries.isEmpty()) {
                    return 0
                }

                if (DEBUG) println("Found ${binaryEntries.size} binaries in ${jarPath.name}")

                // Extract each binary, sign it, and update the JAR
                for (binaryEntry in binaryEntries) {
                    if (DEBUG) println("Processing JAR binary: $binaryEntry")

                    // Extract the specific binary
                    val extractCommand = Command("jar", "-xf", jarPath.toString(), binaryEntry)
                    extractCommand.workingDir(tempDir.toString())
                    val extractExitCode = extractCommand.exec().waitFor()

                    if (extractExitCode == 0) {
                        // The binary is extracted preserving directory structure
                        val extractedBinaryPath = tempDir / binaryEntry

                        if (fs.exists(extractedBinaryPath)) {
                            // Sign the extracted binary
                            if (DEBUG) println("Signing JAR binary: $binaryEntry")
                            signSingleItem(extractedBinaryPath, app, false) // JAR binaries are always libraries

                            // Update the JAR with the signed binary
                            val updateCommand = Command("jar", "-uf", jarPath.toString(), binaryEntry)
                            updateCommand.workingDir(tempDir.toString())
                            val updateExitCode = updateCommand.exec().waitFor()

                            if (updateExitCode == 0) {
                                signedCount++
                                if (DEBUG) println("✓ Updated JAR with signed binary: $binaryEntry")
                            } else {
                                if (DEBUG) println("✗ Failed to update JAR with signed binary: $binaryEntry")
                            }
                        } else {
                            if (DEBUG) println("✗ Extracted binary not found: $extractedBinaryPath")
                            // List what was actually extracted for debugging
                            if (DEBUG) {
                                try {
                                    println("Contents of temp dir:")
                                    fs.listRecursively(tempDir).forEach { file ->
                                        println("  $file")
                                    }
                                } catch (e: Exception) {
                                    println("Could not list temp dir: ${e.message}")
                                }
                            }
                        }
                    } else {
                        if (DEBUG) println("✗ Could not extract binary: $binaryEntry (exit code: $extractExitCode)")
                    }
                }
            } finally {
                // Clean up temp directory
                FileUtils.safeDelete(fs, tempDir)
            }
        } catch (e: Exception) {
            if (DEBUG) println("Warning: Could not process JAR ${jarPath.name}: ${e.message}")
        }

        return signedCount
    }

    private fun isSignableBinaryPath(path: String): Boolean {
        val name = path.lowercase()
        return name.endsWith(".dylib") || name.endsWith(".jnilib") || name.endsWith(".so")
    }

    private suspend fun signSingleItem(path: okio.Path, app: Application, withRuntime: Boolean) {
        val p12File = app.p12File!!
        val p12Pass = app.p12Pass!!
        // Build the rcodesign command
        val rcodesignArgs = mutableListOf(
            "rcodesign", "sign",
            "--p12-file", "/certs/${p12File.toPath().name}",
            "--p12-password-file", "/secrets/${p12Pass.toPath().name}",
            "--timestamp-url", "http://timestamp.apple.com/ts01",
            "--for-notarization"
        )

        // Add runtime flag only for executables and app bundles
        if (withRuntime) {
            rcodesignArgs.addAll(listOf("--code-signature-flags", "runtime"))
        }

        rcodesignArgs.add("/work/${path.name}")

        val command = Command(
            "docker", "run", "--rm", "-t",
            "-v", "${path.parent}:/work",
            "-v", "${p12File.toPath().parent}:/certs",
            "-v", "${p12Pass.toPath().parent}:/secrets",
            "docker.io/teras/appimage-builder",
            "sh", "-c", "${rcodesignArgs.joinToString(" ")}"
        )

        val outputBuffer = StringBuilder()
        val errorBuffer = StringBuilder()

        command.addOutListener { output ->
            outputBuffer.append(output.decodeToString())
        }
        command.addErrorListener { error ->
            errorBuffer.append(error.decodeToString())
        }

        val exitCode = command.exec().waitFor()
        if (exitCode == 0) {
            if (DEBUG) println("✓ Successfully signed: ${path.name}")
            // Verify the signature was actually applied
            verifySignature(path, app)
        } else {
            if (DEBUG) {
                println("✗ Failed to sign: ${path.name} (exit code: $exitCode)")
                if (outputBuffer.isNotEmpty()) {
                    println("Output: ${outputBuffer.toString().trim()}")
                }
                if (errorBuffer.isNotEmpty()) {
                    println("Error: ${errorBuffer.toString().trim()}")
                }
            }
        }
    }

    private suspend fun verifySignature(path: okio.Path, app: Application) {
        val verifyCommand = listOf(
            "docker", "run", "--rm", "-t",
            "-v", "${path.parent}:/work",
            "docker.io/teras/appimage-builder",
            "rcodesign", "verify",
            "/work/${path.name}"
        )

        val command = Command(*verifyCommand.toTypedArray())
        val outputBuffer = StringBuilder()
        val errorBuffer = StringBuilder()

        command.addOutListener { output ->
            outputBuffer.append(output.decodeToString())
        }
        command.addErrorListener { error ->
            errorBuffer.append(error.decodeToString())
        }

        val exitCode = command.exec().waitFor()
        if (exitCode == 0) {
            if (DEBUG) println("  ✓ Signature verified: ${path.name}")
        } else {
            if (DEBUG) {
                println("  ✗ Signature verification failed: ${path.name}")
                if (outputBuffer.isNotEmpty()) {
                    println("    Output: ${outputBuffer.toString().trim()}")
                }
                if (errorBuffer.isNotEmpty()) {
                    println("    Error: ${errorBuffer.toString().trim()}")
                }
            }
        }
    }

    private suspend fun notarizeMacOSApp(path: okio.Path, app: Application) {
        val notaryJson = app.notaryJson
        if (notaryJson == null) {
            if (DEBUG) println("Warning: Missing notary JSON file, skipping notarization")
            return
        }

        val fs = FileSystem.SYSTEM
        if (!fs.exists(notaryJson.toPath())) {
            if (DEBUG) println("Warning: Notary JSON file not found: $notaryJson")
            return
        }

        if (DEBUG) println("Notarizing ${if (path.name.endsWith(".zip")) "ZIP" else "DMG"}: ${path.name}")

        val notarizeCommand = Command(
            "docker",
            "run",
            "--rm",
            "-t",
            "-v",
            "${path.parent}:/work",
            "-v",
            "${notaryJson.toPath().parent}:/config",
            "docker.io/teras/appimage-builder",
            "sh",
            "-c",
            "rcodesign notary-submit --api-key-file /config/${notaryJson.toPath().name} --staple /work/${path.name}"
        )

        val outputBuffer = StringBuilder()
        val errorBuffer = StringBuilder()

        notarizeCommand.addOutListener { output ->
            outputBuffer.append(output.decodeToString())
        }
        notarizeCommand.addErrorListener { error ->
            errorBuffer.append(error.decodeToString())
        }

        val exitCode = notarizeCommand.exec().waitFor()
        if (exitCode == 0) {
            if (DEBUG) println("Successfully notarized ${if (path.name.endsWith(".zip")) "ZIP" else "DMG"}: ${path.name}")
        } else {
            if (DEBUG) {
                println("Warning: Failed to notarize ${if (path.name.endsWith(".zip")) "ZIP" else "DMG"}: ${path.name} (exit code: $exitCode)")
                if (outputBuffer.isNotEmpty()) {
                    println("Output: ${outputBuffer.toString().trim()}")
                }
                if (errorBuffer.isNotEmpty()) {
                    println("Error: ${errorBuffer.toString().trim()}")
                }
            }
        }
    }

}