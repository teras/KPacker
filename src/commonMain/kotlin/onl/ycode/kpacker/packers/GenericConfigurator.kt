package onl.ycode.kpacker.packers

import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.SYSTEM
import onl.ycode.koren.Command
import onl.ycode.koren.Files
import onl.ycode.koren.PosixPermissions.*
import onl.ycode.kpacker.Application
import onl.ycode.kpacker.DEBUG
import onl.ycode.kpacker.utils.TarUtils

object GenericConfigurator : Configurator {
    override suspend fun fetchDist(): Path {
        // Generic target doesn't bundle JRE, return a dummy path
        // This method won't be called for GenericConfigurator due to special handling in packApp
        return FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "generic-unused"
    }

    override fun baseDirName(name: String): String = name

    override fun binLocation(name: String): String = name

    override val appLocation: String = "lib"

    override suspend fun postProcess(targetDir: String, app: Application) {
        val fs = FileSystem.SYSTEM
        val targetPath = targetDir.toPath()
        val appDir = targetPath / app.name

        if (DEBUG) println("Creating generic package for ${app.name}")

        // Create launcher script
        createLauncherScript(appDir, app)

        // Create tar.gz archive
        createTarGzArchive(targetPath, app)

        // Clean up the directory, keep only the tar.gz
        fs.deleteRecursively(appDir)
    }

    private suspend fun createLauncherScript(appDir: Path, app: Application) {
        val fs = FileSystem.SYSTEM
        val launcherScript = appDir / app.name

        val scriptContent = buildString {
            appendLine("#!/bin/bash")
            appendLine()
            appendLine("# ${app.name} Launcher Script")
            appendLine("# Version: ${app.version}")
            appendLine()
            appendLine("# Get the directory where this script is located")
            appendLine("SCRIPT_DIR=\"\$(cd \"\$(dirname \"\${BASH_SOURCE[0]}\")\" && pwd)\"")
            appendLine()
            appendLine("# Set up classpath")
            appendLine("CLASSPATH=\"\$SCRIPT_DIR/lib/*\"")
            appendLine()
            appendLine("# Launch the application")
            appendLine("java -cp \"\$CLASSPATH\" ${app.mainClass} \"\$@\"")
        }

        fs.write(launcherScript) {
            writeUtf8(scriptContent)
        }

        // Make script executable
        Files.addPermissions(
            launcherScript.toString(),
            OWNER_EXECUTE,
            GROUP_EXECUTE,
            OTHERS_EXECUTE
        )

        if (DEBUG) println("Created launcher script: $launcherScript")
    }

    private suspend fun createTarGzArchive(targetPath: Path, app: Application) {
        val fs = FileSystem.SYSTEM
        val appDir = targetPath / app.name
        val archiveName = "${app.name}-${app.version}-generic.tar.gz"
        val archivePath = targetPath / archiveName

        if (DEBUG) println("Creating tar.gz archive: $archivePath")

        TarUtils.createTarGz(appDir, archivePath)

        if (DEBUG) println("Created generic package: $archiveName")
    }
}