/*
 * SPDX-License-Identifier: BSD-3-Clause
 * Copyright (c) 2025, KPacker Contributors
 */


package onl.ycode.kpacker.utils

import okio.FileSystem
import okio.Path
import okio.SYSTEM
import onl.ycode.koren.Command
import onl.ycode.kpacker.DEBUG

object TarUtils {
    val fs = FileSystem.SYSTEM

    suspend fun createTarGz(sourceDir: Path, targetFile: Path) {
        if (DEBUG) println("Creating tar.gz: $sourceDir -> $targetFile")

        // Use tar command to create compressed archive
        val command = Command(
            "tar",
            "-czf",
            targetFile.toString(),
            "-C",
            sourceDir.parent.toString(),
            sourceDir.name
        )

        val exitCode = command.exec().waitFor()
        if (exitCode != 0) {
            throw RuntimeException("Failed to create tar.gz with exit code: $exitCode")
        }

        if (DEBUG) println("Successfully created tar.gz: $targetFile")
    }
}