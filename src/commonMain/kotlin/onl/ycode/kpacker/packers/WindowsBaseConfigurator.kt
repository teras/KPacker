/*
 * SPDX-License-Identifier: BSD-3-Clause
 * Copyright (c) 2025, KPacker Contributors
 */


package onl.ycode.kpacker.packers

import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM
import onl.ycode.kpacker.Application
import onl.ycode.kpacker.DEBUG
import onl.ycode.kpacker.utils.ContainerFactory
import onl.ycode.kpacker.utils.FileUtils
import onl.ycode.kpacker.utils.TempFolderRegistry

abstract class WindowsBaseConfigurator : Configurator {
    override fun baseDirName(name: String) = name
    override fun binLocation(name: String) = "$name.exe"
    override val appLocation = "app"

    // Abstract properties for Windows architecture
    abstract val is64Bit: Boolean
    abstract val architecture: String // "x64"

    override suspend fun postProcess(targetDir: String, app: Application) {
        createWindowsInstaller(targetDir, app)
    }

    private suspend fun createWindowsInstaller(outputDir: String, app: Application) {
        val fs = FileSystem.SYSTEM
        val outputDirPath = outputDir.toPath()

        if (DEBUG) println("Creating Windows installer for ${app.name} ($architecture)")

        // Create temporary directory for installer resources
        val installerResDir = TempFolderRegistry.createTempFolder("installer-", "-res").toPath()

        try {
            // Handle icon conversion and embedding - now using standardized PNG input
            val iconPath = app.getIconPath()
            var hasIcon = false

            if (iconPath != null && fs.exists(iconPath)) {
                if (DEBUG) println("Converting standardized PNG to ICO for Windows installer")

                // Convert standardized PNG to ICO format using ImageMagick in container
                val targetIconPath = installerResDir / "install.ico"
                val appIconPath = installerResDir / "app.ico"

                val runner1 = ContainerFactory.createRunner()
                // Create temporary working directory and copy icon
                try {
                    fs.copy(iconPath, runner1.workingDir / iconPath.name)

                    // Convert PNG to ICO using ImageMagick (create multi-size ICO)
                    val magickCommand = runner1.run(
                        "convert ${iconPath.name} -resize 256x256 -define icon:auto-resize=256,128,64,48,32,16 install.ico"
                    )
                    val magickExitCode = magickCommand.exec().waitFor()
                    if (magickExitCode == 0) {
                        // Copy result back from working directory
                        val resultFile = runner1.workingDir / "install.ico"
                        if (fs.exists(resultFile)) {
                            fs.copy(resultFile, targetIconPath)
                            // Copy for app embedding too
                            fs.copy(targetIconPath, appIconPath)
                            hasIcon = true
                            if (DEBUG) println("Successfully converted PNG to multi-size ICO")

                            // Embed icon into the application executable using ResourceHacker
                            val appDir = outputDirPath / app.name
                            val appExePath = appDir / "${app.name}.exe"

                            if (fs.exists(appExePath)) {
                                // Create working directory for ResourceHacker operation
                                val runner2 = ContainerFactory.createRunner()

                                try {
                                    // Copy exe and ico to working directory
                                    fs.copy(appExePath, runner2.workingDir / "${app.name}.exe")
                                    fs.copy(appIconPath, runner2.workingDir / "app.ico")

                                    val resourceHackerCommand =
                                        runner2.run("resourcehacker -open ${app.name}.exe -save ${app.name}.exe -action addoverwrite -res app.ico -mask ICONGROUP,MAINICON")
                                    val rhExitCode = resourceHackerCommand.exec().waitFor()
                                    if (rhExitCode == 0) {
                                        // Copy modified exe back
                                        val resultExe = runner2.workingDir / "${app.name}.exe"
                                        if (fs.exists(resultExe)) {
                                            fs.copy(resultExe, appExePath)
                                            if (DEBUG) println("Embedded icon into ${app.name}.exe using ResourceHacker")
                                        }
                                    } else {
                                        if (DEBUG) println("Warning: Could not embed icon using ResourceHacker")
                                    }
                                } finally {
                                    // Clean up ResourceHacker working directory
                                    try {
                                        fs.deleteRecursively(runner2.workingDir)
                                    } catch (e: Exception) {
                                        if (DEBUG) println("Failed to clean up ResourceHacker directory: ${e.message}")
                                    }
                                }
                            }
                        }
                    } else {
                        if (DEBUG) println("Warning: Could not convert PNG to ICO using ImageMagick")
                    }
                } finally {
                    // Clean up icon conversion working directory
                    try {
                        fs.deleteRecursively(runner1.workingDir)
                    } catch (e: Exception) {
                        if (DEBUG) println("Failed to clean up icon working directory: ${e.message}")
                    }
                }
            }

            // Generate Inno Setup script
            val issContent = generateInnoSetupScript(app, hasIcon)
            fs.write(installerResDir / "installer.iss") { writeUtf8(issContent) }

            // Use container to create Windows installer
            val runner3 = ContainerFactory.createRunner()

            val appDir = outputDirPath / app.name

            // Create working directory and copy files for container
            try {
                // Copy installer resources to working directory
                FileUtils.copyDirectory(fs, installerResDir, runner3.workingDir)
                // Copy app directory to working/app
                FileUtils.copyDirectory(fs, appDir, runner3.workingDir / "app")

                val cmd = runner3.run("innosetup installer.iss")

                cmd.addOutListener { output ->
                    if (DEBUG) print(output.decodeToString())
                }
                cmd.addErrorListener { error ->
                    if (DEBUG) print("ERROR: ${error.decodeToString()}")
                }

                val exitCode = cmd.exec().waitFor()
                if (exitCode != 0) {
                    throw RuntimeException("Windows installer creation failed with exit code $exitCode")
                }

                // Copy the generated installer back from working directory
                val generatedInstaller = runner3.workingDir / "${app.name}.exe"
                if (fs.exists(generatedInstaller)) {
                    val finalInstallerPath = outputDirPath / "${app.name}-${app.version}-$architecture.exe"
                    fs.copy(generatedInstaller, finalInstallerPath)

                    if (DEBUG) println("Windows installer created: $finalInstallerPath")
                } else {
                    println("Warning: No installer file found after creation")
                }
            } finally {
                // Clean up working directory
                try {
                    fs.deleteRecursively(runner3.workingDir)
                } catch (e: Exception) {
                    if (DEBUG) println("Failed to clean up installer working directory: ${e.message}")
                }
            }

        } finally {
            // Cleanup installer resources directory
            TempFolderRegistry.deleteTempFolder(installerResDir.toString())
        }
    }

    private fun generateInnoSetupScript(app: Application, hasIcon: Boolean): String {
        return buildString {
            // Define constants
            appendLine("#define AppName \"${app.name}\"")
            appendLine("#define AppVersion \"${app.version}\"")
            appendLine()

            // Setup section
            appendLine("[Setup]")
            appendLine("AppId={{${generateAppId(app.name)}}")
            appendLine("AppName={#AppName}")
            appendLine("AppVersion={#AppVersion}")
            appendLine("AppPublisher={#AppName}")
            appendLine("DefaultDirName={commonpf}\\{#AppName}")
            appendLine("DefaultGroupName={#AppName}")
            appendLine("OutputDir=.")
            appendLine("OutputBaseFilename={#AppName}")
            appendLine("Compression=lzma2")
            appendLine("SolidCompression=yes")
            appendLine("WizardStyle=modern")
            appendLine("DisableReadyPage=yes")
            appendLine("DisableWelcomePage=no")
            appendLine("UninstallDisplayIcon={app}\\{#AppName}.exe")
            appendLine("CreateAppDir=yes")
            appendLine("UsePreviousAppDir=no")
            appendLine("WizardResizable=no")
            appendLine("ShowLanguageDialog=no")

            // 64-bit specific settings
            if (is64Bit) {
                appendLine("ArchitecturesInstallIn64BitMode=x64compatible")
                appendLine("ArchitecturesAllowed=x64compatible")
            }

            // Add icon if available
            if (hasIcon) {
                appendLine("SetupIconFile=install.ico")
            }

            appendLine()

            // Messages section for better text hierarchy
            appendLine("[Messages]")
            appendLine("WelcomeLabel1=Welcome to the [name] Setup Wizard")
            appendLine("WelcomeLabel2=This will install [name/ver] on your computer.%n%nIt is recommended that you close all other applications before continuing.")
            appendLine("ClickNext=Click Next to continue.")
            appendLine("FinishedHeadingLabel=[name] has been successfully installed")
            appendLine()

            // Files section
            appendLine("[Files]")
            appendLine("Source: \"app\\*\"; DestDir: \"{app}\"; Flags: ignoreversion recursesubdirs createallsubdirs")
            appendLine()

            // Icons section
            appendLine("[Icons]")
            appendLine("Name: \"{group}\\{#AppName}\"; Filename: \"{app}\\{#AppName}.exe\"; WorkingDir: \"{app}\"")
            appendLine("Name: \"{group}\\Uninstall {#AppName}\"; Filename: \"{uninstallexe}\"")
            appendLine("Name: \"{autodesktop}\\{#AppName}\"; Filename: \"{app}\\{#AppName}.exe\"")
            appendLine()

            // Run section
            appendLine("[Run]")
            appendLine("Filename: \"{app}\\{#AppName}.exe\"; Description: \"Launch {#AppName}\"; Flags: nowait postinstall skipifsilent")
        }
    }

    private fun generateAppId(appName: String): String {
        // Generate a simple UUID-like string based on app name
        val hash = appName.hashCode().toUInt()
        return "${hash.toString(16).padStart(8, '0')}-0000-0000-0000-000000000000".uppercase()
    }
}