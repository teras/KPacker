package onl.ycode.kpacker.packers

import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM
import onl.ycode.koren.Command
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

                val containerRunner = ContainerFactory.createRunner()
                if (containerRunner != null) {
                    // Convert PNG to ICO using ImageMagick (create multi-size ICO)
                    val magickCommand = containerRunner.run(
                        "run", "--rm", "-t",
                        "-v", "${iconPath.parent}:/input",
                        "-v", "${targetIconPath.parent}:/output",
                        "docker.io/teras/appimage-builder",
                        "sh", "-c", "convert /input/${iconPath.name} -resize 256x256 -define icon:auto-resize=256,128,64,48,32,16 /output/install.ico && chown \$(stat -c '%u:%g' /input) /output/install.ico"
                    )
                    val magickExitCode = magickCommand.exec().waitFor()
                    if (magickExitCode == 0) {
                        // Copy for app embedding too
                        fs.copy(targetIconPath, appIconPath)
                        hasIcon = true
                        if (DEBUG) println("Successfully converted PNG to multi-size ICO")

                        // Embed icon into the application executable using ResourceHacker
                        val appDir = outputDirPath / app.name
                        val appExePath = appDir / "${app.name}.exe"

                        if (fs.exists(appExePath)) {
                            val resourceHackerCommand = containerRunner.run(
                                "run", "--rm", "-t",
                                "-v", "$appDir:/app",
                                "-v", "${appIconPath.parent}:/icons",
                                "docker.io/teras/appimage-builder",
                                "sh", "-c", "resourcehacker -open /app/${app.name}.exe -save /app/${app.name}.exe -action addoverwrite -res /icons/app.ico -mask ICONGROUP,MAINICON && chown \$(stat -c '%u:%g' /app) /app/${app.name}.exe"
                            )
                            val rhExitCode = resourceHackerCommand.exec().waitFor()
                            if (rhExitCode == 0) {
                                if (DEBUG) println("Embedded icon into ${app.name}.exe using ResourceHacker")
                            } else {
                                if (DEBUG) println("Warning: Could not embed icon using ResourceHacker")
                            }
                        }
                    } else {
                        if (DEBUG) println("Warning: Could not convert PNG to ICO using ImageMagick")
                    }
                } else {
                    if (DEBUG) println("No container runtime available for icon conversion")
                }
            }

            // Generate Inno Setup script
            val issContent = generateInnoSetupScript(app, installerResDir, hasIcon)
            fs.write(installerResDir / "installer.iss") { writeUtf8(issContent) }

            // Use container to create Windows installer
            val containerRunner = ContainerFactory.createRunner()
                ?: throw RuntimeException("No container runtime (Docker or Podman) found in PATH")

            val appDir = outputDirPath / app.name
            val containerImage = "docker.io/teras/appimage-builder"

            val cmd = containerRunner.run(
                "run", "--rm", "-t",
                "-v", "$installerResDir:/work",
                "-v", "$appDir:/work/app",
                "-w", "/work",
                containerImage,
                "innosetup", "installer.iss"
            )

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

            // Move the generated installer to the output directory
            val generatedInstaller = installerResDir / "${app.name}.exe"
            if (fs.exists(generatedInstaller)) {
                val finalInstallerPath = outputDirPath / "${app.name}-${app.version}-$architecture.exe"
                FileUtils.safeMove(fs, generatedInstaller, finalInstallerPath)

                if (DEBUG) println("Windows installer created: $finalInstallerPath")
            } else {
                println("Warning: No installer file found after creation")
            }

        } finally {
            // Cleanup installer resources directory
            TempFolderRegistry.deleteTempFolder(installerResDir.toString())
        }
    }

    private fun generateInnoSetupScript(app: Application, installerResDir: okio.Path, hasIcon: Boolean): String {
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