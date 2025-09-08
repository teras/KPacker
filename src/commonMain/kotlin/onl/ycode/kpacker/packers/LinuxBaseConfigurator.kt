package onl.ycode.kpacker.packers

import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM
import onl.ycode.koren.Command
import onl.ycode.koren.Files
import onl.ycode.koren.PosixPermissions.*
import onl.ycode.kpacker.Application
import onl.ycode.kpacker.DEBUG
import onl.ycode.kpacker.utils.ContainerFactory
import onl.ycode.kpacker.utils.FileUtils
import onl.ycode.kpacker.utils.TempFolderRegistry

abstract class LinuxBaseConfigurator : Configurator {
    override fun baseDirName(name: String) = name
    override fun binLocation(name: String) = "bin/$name"
    override val appLocation = "lib/app"

    // Abstract property for architecture (x86_64 or aarch64)
    abstract val architecture: String

    override suspend fun postProcess(targetDir: String, app: Application) {
        createAppImage(targetDir, app)
    }

    private suspend fun createAppImage(outputDir: String, app: Application) {
        val fs = FileSystem.SYSTEM
        val outputDirPath = outputDir.toPath()
        val appDir = outputDirPath / app.name
        val cname = app.name.lowercase().replace(Regex("[^a-z0-9]"), "")

        // Create desktop file
        val desktop = buildString {
            appendLine("[Desktop Entry]")
            appendLine("Type=Application")
            appendLine("Name=${app.name}")
            appendLine("Exec=$cname %u")
            appendLine("Categories=Development;")
            appendLine("Comment=${app.name} application")
            appendLine("Icon=$cname")
        }

        fs.write(appDir / "$cname.desktop") { writeUtf8(desktop) }

        // Get standardized PNG icon and copy it
        val iconPath = app.getIconPath()
        if (iconPath != null && fs.exists(iconPath)) {
            val targetIconPath = appDir / "${app.name.lowercase()}.png"
            FileUtils.safeCopy(fs, iconPath, targetIconPath)
            if (DEBUG) println("Copied standardized PNG icon for Linux AppImage")
        }

        // Create AppRun as symlink to the binary
        val lnCmd = Command("ln", "-sf", "bin/${app.name}", (appDir / "AppRun").toString())
        lnCmd.exec().waitFor()

        if (DEBUG) println("Creating AppImage for ${app.name} ($architecture)")

        // Create build directory for AppImage builder using registry
        val buildDir = TempFolderRegistry.createTempFolder("appimage-", "-build").toPath()

        try {
            // Use container to create AppImage - based on Nim makeapp logic
            val containerRunner = ContainerFactory.createRunner()
                ?: throw RuntimeException("No container runtime (Docker or Podman) found in PATH")

            val cmd = containerRunner.run(
                "run", "--rm", "-t",
                "-v", "$buildDir:/usr/src/app",
                "-v", "$appDir:/usr/src/app/${app.name}",
                "teras/appimage-builder",
                "bash", "-c",
                "export VERSION=${app.version} ARCH=$architecture && /opt/appimage/AppRun ${app.name} && chown -R \$(stat -c '%u:%g' /usr/src/app) /usr/src/app"
            )

            cmd.addOutListener { output ->
                if (DEBUG) print(output.decodeToString())
            }
            cmd.addErrorListener { error ->
                if (DEBUG) print("ERROR: ${error.decodeToString()}")
            }

            val exitCode = cmd.exec().waitFor()
            if (exitCode != 0) {
                throw RuntimeException("AppImage creation failed with exit code $exitCode")
            }

            // Find generated AppImage and move it to the output directory
            val appImageFiles = fs.list(buildDir).filter { it.name.endsWith(".AppImage") }
            if (appImageFiles.isNotEmpty()) {
                val appImagePath = appImageFiles.first()
                val finalAppImagePath = outputDirPath / "${app.name}-${app.version}-$architecture.AppImage"
                FileUtils.safeMove(fs, appImagePath, finalAppImagePath)

                // Make executable
                Files.addPermissions(
                    finalAppImagePath.toString(),
                    OWNER_EXECUTE,
                    GROUP_EXECUTE,
                    OTHERS_EXECUTE
                )

                if (DEBUG) println("AppImage created: $finalAppImagePath")
            } else {
                println("Warning: No AppImage file found after creation")
            }

        } finally {
            // Cleanup build directory via registry
            TempFolderRegistry.deleteTempFolder(buildDir.toString())
        }
    }
}