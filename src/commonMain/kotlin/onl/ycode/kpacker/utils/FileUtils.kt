/*
 * SPDX-License-Identifier: BSD-3-Clause
 * Copyright (c) 2025, KPacker Contributors
 */


package onl.ycode.kpacker.utils

import okio.FileSystem
import okio.Path
import onl.ycode.kpacker.DEBUG

object FileUtils {

    /**
     * Safely moves a file from source to destination, handling cross-filesystem moves.
     * Falls back to copy+delete if atomic move fails.
     */
    fun safeMove(fs: FileSystem, source: Path, destination: Path) {
        if (!fs.exists(source)) {
            throw IllegalArgumentException("Source file does not exist: $source")
        }

        try {
            fs.atomicMove(source, destination)
            if (DEBUG) println("Moved file: $source -> $destination")
        } catch (e: Exception) {
            // Fallback to copy+delete if atomic move fails (e.g., cross-filesystem)
            if (DEBUG) println("Atomic move failed, using copy+delete: ${e.message}")
            fs.copy(source, destination)
            fs.delete(source)
            if (DEBUG) println("Copied and deleted file: $source -> $destination")
        }
    }

    /**
     * Safely copies a file from source to destination, creating parent directories if needed.
     */
    suspend fun safeCopy(fs: FileSystem, source: Path, destination: Path) {
        if (!fs.exists(source)) {
            throw IllegalArgumentException("Source file does not exist: $source")
        }

        // Create parent directories if they don't exist
        destination.parent?.let { parent ->
            if (!fs.exists(parent)) {
                fs.createDirectories(parent)
            }
        }

        fs.copy(source, destination)

        // Preserve permissions (important for executables)
        try {
            onl.ycode.koren.Files.setPermissions(destination.toString(), *onl.ycode.koren.Files.getPermissions(source.toString()).toTypedArray())
        } catch (e: Exception) {
            if (DEBUG) println("Warning: Could not preserve permissions for $destination: ${e.message}")
        }
    }

    /**
     * Recursively copies a directory from source to destination.
     */
    suspend fun copyDirectory(fs: FileSystem, source: Path, destination: Path) {
        val count = copyDirectoryWithCount(fs, source, destination)
        if (DEBUG && count > 0) println("Total copied: $count files")
    }

    private suspend fun copyDirectoryWithCount(fs: FileSystem, source: Path, destination: Path): Int {
        if (!fs.exists(source)) return 0

        var count = 0
        val metadata = fs.metadataOrNull(source) ?: return 0

        // Handle symlinks - preserve them as symlinks
        if (metadata.symlinkTarget != null) {
            val target = metadata.symlinkTarget!!
            // Create symlink at destination pointing to the same target
            onl.ycode.koren.Files.createSymlink(target.toString(), destination.toString())
            if (DEBUG) println("Preserved symlink: $source -> $target")
            return 1
        }

        if (metadata.isDirectory) {
            if (!fs.exists(destination)) {
                fs.createDirectories(destination)
            }

            fs.list(source).forEach { child ->
                val childName = child.name
                count += copyDirectoryWithCount(fs, source / childName, destination / childName)
            }
        } else {
            safeCopy(fs, source, destination)
            count = 1
        }
        return count
    }

    /**
     * Safely deletes a file or directory, suppressing errors if it doesn't exist.
     */
    fun safeDelete(fs: FileSystem, path: Path) {
        try {
            if (fs.exists(path)) {
                fs.deleteRecursively(path)
                if (DEBUG) println("Deleted: $path")
            }
        } catch (e: Exception) {
            if (DEBUG) println("Warning: Could not delete $path: ${e.message}")
        }
    }
}