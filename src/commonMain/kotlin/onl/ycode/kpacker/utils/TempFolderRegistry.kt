package onl.ycode.kpacker.utils

import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM
import onl.ycode.koren.Files
import onl.ycode.kpacker.DEBUG

object TempFolderRegistry {
    private val tempFolders = mutableSetOf<String>()
    private val fs = FileSystem.SYSTEM

    /**
     * Create a temporary directory and register it for cleanup
     */
    fun createTempFolder(prefix: String = "kpacker-", suffix: String = "-temp"): String {
        val tempDir = Files.tempDir(prefix, suffix)
        tempFolders.add(tempDir)
        if (DEBUG) println("Created temp directory: $tempDir")
        return tempDir
    }

    /**
     * Delete a specific temporary directory and remove it from the registry
     */
    fun deleteTempFolder(tempDir: String): Boolean {
        if (tempFolders.remove(tempDir)) {
            try {
                fs.deleteRecursively(tempDir.toPath())
                if (DEBUG) println("Deleted temp directory: $tempDir")
                return true
            } catch (e: Exception) {
                if (DEBUG) println("Failed to delete temp directory $tempDir: ${e.message}")
                return false
            }
        }
        return false
    }

    /**
     * Clean up all registered temporary directories
     */
    fun cleanupAll() {
        val toDelete = tempFolders.toList()
        tempFolders.clear()

        toDelete.forEach { tempDir ->
            try {
                fs.deleteRecursively(tempDir.toPath())
                if (DEBUG) println("Cleaned up temp directory: $tempDir")
            } catch (e: Exception) {
                if (DEBUG) println("Failed to cleanup temp directory $tempDir: ${e.message}")
            }
        }

        if (toDelete.isNotEmpty() && DEBUG) {
            println("Cleaned up ${toDelete.size} temporary directories")
        }
    }

    /**
     * Get count of currently registered temp folders (for debugging)
     */
    fun getRegisteredCount(): Int = tempFolders.size
}