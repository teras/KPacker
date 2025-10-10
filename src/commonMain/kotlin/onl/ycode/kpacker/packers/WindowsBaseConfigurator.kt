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
            val installIconPath = app.getInstallIconPath()
            val documentIconPath = app.getDocumentIconPath()
            var hasIcon = false
            var hasDocumentIcon = false

            if (installIconPath != null && fs.exists(installIconPath)) {
                if (DEBUG) println("Converting standardized PNG to ICO for Windows installer")

                // Convert standardized PNG to ICO format using ImageMagick in container
                val targetIconPath = installerResDir / "install.ico"
                val appIconPath = installerResDir / "app.ico"
                val documentIconPathIco = installerResDir / "document.ico"

                val runner1 = ContainerFactory.createRunner()
                // Create temporary working directory and copy icons
                try {
                    fs.copy(installIconPath, runner1.workingDir / installIconPath.name)
                    // Also copy app icon if different from install icon
                    if (iconPath != installIconPath && iconPath != null) {
                        fs.copy(iconPath, runner1.workingDir / iconPath.name)
                    }
                    // Copy document icon if different and document extensions are specified
                    val documentExtensions = app.getDocumentExtensions()
                    if (documentIconPath != null && documentExtensions.isNotEmpty() &&
                        documentIconPath != installIconPath && documentIconPath != iconPath) {
                        fs.copy(documentIconPath, runner1.workingDir / documentIconPath.name)
                    }

                    // Convert PNG to ICO using ImageMagick (create multi-size ICO)
                    val magickCommand = runner1.run(
                        "convert ${installIconPath.name} -resize 256x256 -define icon:auto-resize=256,128,64,48,32,16 install.ico"
                    )
                    val magickExitCode = magickCommand.exec().waitFor()
                    if (magickExitCode == 0) {
                        // Copy result back from working directory
                        val resultFile = runner1.workingDir / "install.ico"
                        if (fs.exists(resultFile)) {
                            fs.copy(resultFile, targetIconPath)
                            hasIcon = true
                            if (DEBUG) println("Successfully converted install icon PNG to multi-size ICO")

                            // Handle app icon separately if different from install icon
                            if (iconPath != null && fs.exists(iconPath)) {
                                // Convert app icon to ICO if different from install icon
                                if (iconPath != installIconPath) {
                                    val appMagickCommand = runner1.run(
                                        "convert ${iconPath.name} -resize 256x256 -define icon:auto-resize=256,128,64,48,32,16 app.ico"
                                    )
                                    val appMagickExitCode = appMagickCommand.exec().waitFor()
                                    if (appMagickExitCode == 0) {
                                        val appResultFile = runner1.workingDir / "app.ico"
                                        if (fs.exists(appResultFile)) {
                                            fs.copy(appResultFile, appIconPath)
                                            if (DEBUG) println("Successfully converted app icon PNG to ICO")
                                        }
                                    } else {
                                        // Fall back to using install icon for app
                                        fs.copy(targetIconPath, appIconPath)
                                        if (DEBUG) println("Using install icon for app icon as fallback")
                                    }
                                } else {
                                    // Install icon and app icon are the same
                                    fs.copy(targetIconPath, appIconPath)
                                }
                            } else {
                                // No app icon specified, use install icon
                                fs.copy(targetIconPath, appIconPath)
                            }

                            // Handle document icon if document extensions are specified
                            val documentExtensions = app.getDocumentExtensions()
                            if (documentExtensions.isNotEmpty()) {
                                // documentIconPath is guaranteed to be non-null when documentExtensions is present due to argos constraint
                                val docIconPath = documentIconPath!!
                                if (docIconPath != installIconPath && docIconPath != iconPath) {
                                    // Convert document icon to ICO
                                    val docMagickCommand = runner1.run(
                                        "convert ${docIconPath.name} -resize 256x256 -define icon:auto-resize=256,128,64,48,32,16 document.ico"
                                    )
                                    val docMagickExitCode = docMagickCommand.exec().waitFor()
                                    if (docMagickExitCode == 0) {
                                        val docResultFile = runner1.workingDir / "document.ico"
                                        if (fs.exists(docResultFile)) {
                                            fs.copy(docResultFile, documentIconPathIco)
                                            hasDocumentIcon = true
                                            if (DEBUG) println("Successfully converted document icon PNG to ICO")
                                        }
                                    }
                                } else if (docIconPath == iconPath) {
                                    // Document icon is same as app icon
                                    fs.copy(appIconPath, documentIconPathIco)
                                    hasDocumentIcon = true
                                } else {
                                    // Document icon is same as install icon
                                    fs.copy(targetIconPath, documentIconPathIco)
                                    hasDocumentIcon = true
                                }
                            }

                            // Embed icon and update version resources in the application executable
                            val appDir = outputDirPath / app.name
                            val appExePath = appDir / "${app.name}.exe"

                            if (fs.exists(appExePath)) {
                                // Create working directory for ResourceHacker operation
                                val runner2 = ContainerFactory.createRunner()

                                try {
                                    // Copy exe and ico to working directory
                                    fs.copy(appExePath, runner2.workingDir / "${app.name}.exe")
                                    fs.copy(appIconPath, runner2.workingDir / "app.ico")

                                    // Step 1: Embed icon
                                    val resourceHackerCommand =
                                        runner2.run("resourcehacker -open ${app.name}.exe -save ${app.name}.exe -action addoverwrite -res app.ico -mask ICONGROUP,MAINICON")
                                    val rhExitCode = resourceHackerCommand.exec().waitFor()
                                    if (rhExitCode == 0) {
                                        if (DEBUG) println("Embedded icon into ${app.name}.exe using ResourceHacker")
                                    } else {
                                        if (DEBUG) println("Warning: Could not embed icon using ResourceHacker")
                                    }

                                    if (DEBUG) println("Starting version info update for ${app.name}.exe")
                                    // Step 2: Generate version info from embedded template and embed it

                                    // Generate version info RC from template using app metadata
                                    val versionRcContent = generateVersionInfoRC(app)

                                    // Write the RC file (UTF-8)
                                    val versionRcPath = runner2.workingDir / "version.rc"
                                    fs.write(versionRcPath) { writeUtf8(versionRcContent) }
                                    if (DEBUG) println("Generated version.rc for ${app.name} version ${app.version}")

                                    // Compile the RC file to RES format
                                    val compileCmd = runner2.run("resourcehacker -open version.rc -save version.res -action compile")
                                    if (compileCmd.exec().waitFor() == 0) {
                                        if (DEBUG) println("Compiled version.rc successfully")

                                        // Delete all existing VERSIONINFO resources first (all IDs and languages)
                                        val deleteCmd = runner2.run("resourcehacker -open ${app.name}.exe -save ${app.name}_clean.exe -action delete -mask VERSIONINFO,,")
                                        if (deleteCmd.exec().waitFor() == 0) {
                                            if (DEBUG) println("Deleted all existing VERSIONINFO resources")

                                            // Add the new compiled RES file (use 'add' not 'addoverwrite' since we deleted all existing)
                                            val embedCmd = runner2.run("resourcehacker -open ${app.name}_clean.exe -save ${app.name}_new.exe -action add -res version.res")
                                            if (embedCmd.exec().waitFor() == 0) {
                                                // Replace original with modified
                                                runner2.run("mv ${app.name}_new.exe ${app.name}.exe").exec().waitFor()
                                                if (DEBUG) println("Updated version info in ${app.name}.exe with version ${app.version}")
                                            } else {
                                                if (DEBUG) println("Warning: Failed to embed version info")
                                            }
                                        } else {
                                            if (DEBUG) println("Warning: Failed to delete existing VERSIONINFO resources")
                                        }
                                    } else {
                                        if (DEBUG) println("Warning: Failed to compile version.rc")
                                    }

                                    // Copy modified exe back
                                    val resultExe = runner2.workingDir / "${app.name}.exe"
                                    if (fs.exists(resultExe)) {
                                        fs.copy(resultExe, appExePath)
                                        if (DEBUG) println("Successfully updated ${app.name}.exe with icon and version info")
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
            val issContent = generateInnoSetupScript(app, hasIcon, hasDocumentIcon)
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

    private fun generateInnoSetupScript(app: Application, hasIcon: Boolean, hasDocumentIcon: Boolean): String {
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

            // Notify Windows of file association changes if document extensions are specified
            if (app.getDocumentExtensions().isNotEmpty()) {
                appendLine("ChangesAssociations=yes")
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
            if (hasDocumentIcon) {
                appendLine("Source: \"document.ico\"; DestDir: \"{app}\"; Flags: ignoreversion")
            }
            appendLine()

            // UninstallDelete section - remove empty directories after uninstall
            appendLine("[UninstallDelete]")
            appendLine("Type: dirifempty; Name: \"{app}\\app\"")
            appendLine("Type: dirifempty; Name: \"{app}\\runtime\"")
            appendLine("Type: dirifempty; Name: \"{app}\"")
            appendLine()

            // Icons section
            appendLine("[Icons]")
            appendLine("Name: \"{group}\\{#AppName}\"; Filename: \"{app}\\{#AppName}.exe\"; WorkingDir: \"{app}\"")
            appendLine("Name: \"{group}\\Uninstall {#AppName}\"; Filename: \"{uninstallexe}\"")
            appendLine("Name: \"{autodesktop}\\{#AppName}\"; Filename: \"{app}\\{#AppName}.exe\"")
            appendLine()

            // Registry section for file associations (using simple approach like old installer)
            val documentExtensions = app.getDocumentExtensions()
            if (documentExtensions.isNotEmpty()) {
                appendLine("[Registry]")
                val documentName = app.documentName ?: "${app.name}"

                documentExtensions.forEach { ext ->
                    // Register each file extension to point to app name as ProgID
                    appendLine("Root: HKCR; Subkey: \".${ext}\"; ValueType: string; ValueName: \"\"; ValueData: \"{#AppName}\"; Flags: uninsdeletevalue")
                }

                // Register the ProgID (using app name directly, not compound like "Jubler.Document")
                appendLine("Root: HKCR; Subkey: \"{#AppName}\"; ValueType: string; ValueName: \"\"; ValueData: \"$documentName\"; Flags: uninsdeletekey")

                // Set icon for the ProgID
                if (hasDocumentIcon) {
                    appendLine("Root: HKCR; Subkey: \"{#AppName}\\DefaultIcon\"; ValueType: string; ValueName: \"\"; ValueData: \"{app}\\document.ico,0\"; Flags: uninsdeletekey")
                }

                // Set command to open files with this ProgID
                appendLine("Root: HKCR; Subkey: \"{#AppName}\\shell\\open\\command\"; ValueType: string; ValueName: \"\"; ValueData: \"\"\"{app}\\{#AppName}.exe\"\" \"\"%1\"\"\"; Flags: uninsdeletekey")

                appendLine()
            }

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

    private fun generateVersionInfoRC(app: Application): String {
        // Parse version string (format: X.Y.Z or X.Y.Z-ALPHA, etc.)
        val versionParts = app.version.split("-")[0].split(".")
        val major = versionParts.getOrNull(0)?.toIntOrNull() ?: 1
        val minor = versionParts.getOrNull(1)?.toIntOrNull() ?: 0
        val patch = versionParts.getOrNull(2)?.toIntOrNull() ?: 0
        val build = 0

        // Get current year for copyright
        val currentYear = 2025  // TODO: Use actual system date when available in commonMain

        return """
// Generated by KPacker
1 VERSIONINFO
FILEVERSION $major,$minor,$patch,$build
PRODUCTVERSION $major,$minor,$patch,$build
FILEOS 0x40004
FILETYPE 0x1
{
BLOCK "StringFileInfo"
{
	BLOCK "040904B0"
	{
		VALUE "CompanyName", "${app.name}"
		VALUE "FileDescription", "${app.name}"
		VALUE "FileVersion", "${app.version}"
		VALUE "InternalName", "${app.name}.exe"
		VALUE "LegalCopyright", "Copyright \xA9 $currentYear"
		VALUE "OriginalFilename", "${app.name}.exe"
		VALUE "ProductName", "${app.name}"
		VALUE "ProductVersion", "${app.version}"
	}
}

BLOCK "VarFileInfo"
{
	VALUE "Translation", 0x0409 0x04B0
}
}
        """.trimIndent()
    }
}