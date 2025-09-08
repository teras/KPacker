package onl.ycode.kpacker.packers

import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM
import onl.ycode.koren.Files
import onl.ycode.kpacker.Application
import onl.ycode.kpacker.DEBUG
import onl.ycode.kpacker.utils.FileUtils
import onl.ycode.kpacker.utils.UnzipUtils

suspend fun packApp(targetDir: String, app: Application, conf: Configurator) {
    val fs = FileSystem.SYSTEM
    val finalOutputPath = targetDir.toPath()

    // Create final output directory
    fs.deleteRecursively(finalOutputPath)
    fs.createDirectories(finalOutputPath)

    // Handle generic target differently (no JRE bundling)
    if (conf is GenericConfigurator) {
        // For generic target, create simple directory structure
        val installDir = finalOutputPath / conf.baseDirName(app.name)
        val appDir = installDir / conf.appLocation

        fs.createDirectories(appDir)

        // Copy application files to lib directory
        var dirCount = 0
        var fileCount = 0
        if (DEBUG) println("Copying application files...")

        FileSystem.SYSTEM.listRecursively(app.sourceDir).forEach {
            val targetFile = appDir / it.relativeTo(app.sourceDir)
            if (fs.metadata(it).isDirectory) {
                dirCount++
                fs.createDirectories(targetFile)
            } else {
                fileCount++
                fs.copy(it, targetFile)
                Files.setPermissions(targetFile.toString(), *Files.getPermissions(it.toString()).toTypedArray())
            }
        }

        if (DEBUG) println("Copied $fileCount files and created $dirCount directories")
    } else {
        // Standard JRE-bundled targets
        // Extract JRE directly to final output directory
        val distFrom = conf.fetchDist()
        UnzipUtils.unzip(distFrom, finalOutputPath)

        // Rename the extracted Launcher directory to the app name
        // For macOS, the extracted directory is Launcher.app, for others it's Launcher
        val launcherDirName = if (conf is MacX64Configurator) "Launcher.app" else "Launcher"
        val launcherDir = finalOutputPath / launcherDirName
        val installDir = finalOutputPath / conf.baseDirName(app.name)
        FileUtils.safeMove(fs, launcherDir, installDir)

        val appDir = installDir / conf.appLocation

        // Rename the launcher binary
        FileUtils.safeMove(
            fs,
            installDir / conf.binLocation("Launcher"),
            installDir / conf.binLocation(app.name)
        )

        // Create app directory and install application files directly in final location
        fs.deleteRecursively(appDir)
        fs.createDirectories(appDir)
        fs.write(appDir / "${app.name}.cfg") { writeUtf8(app.createConfig()) }

        var dirCount = 0
        var fileCount = 0
        if (DEBUG) println("Copying application files...")

        FileSystem.SYSTEM.listRecursively(app.sourceDir).forEach {
            val targetFile = appDir / it.relativeTo(app.sourceDir)
            if (fs.metadata(it).isDirectory) {
                dirCount++
                fs.createDirectories(targetFile)
            } else {
                fileCount++
                fs.copy(it, targetFile)
                Files.setPermissions(targetFile.toString(), *Files.getPermissions(it.toString()).toTypedArray())
            }
        }

        if (DEBUG) println("Copied $fileCount files and created $dirCount directories")
    }

    // Post-processing step (e.g., AppImage creation for Linux)
    // AppImage creation will use its own temp directories for building
    conf.postProcess(finalOutputPath.toString(), app)
}


