/*
 * SPDX-License-Identifier: BSD-3-Clause
 * Copyright (c) 2025, KPacker Contributors
 */


package onl.ycode.kpacker.utils

import kotlinx.serialization.json.Json
import okio.*
import okio.Path.Companion.toPath
import onl.ycode.koren.Files
import onl.ycode.koren.PosixPermissions.*
import onl.ycode.kpacker.ArchiveMetaData
import onl.ycode.kpacker.DEBUG

object UnzipUtils {
    fun unzip(from: Path, dest: Path) {
        val metaPath = "/.metadata".toPath()
        var metaData: ArchiveMetaData? = null
        var extractedCount = 0

        if (DEBUG) println("Extracting JRE distribution...")

        FileSystem.SYSTEM.openZip(from).use { zipFs ->
            for (entry in zipFs.listRecursively("/".toPath())) {
                if (zipFs.metadataOrNull(entry)?.isDirectory ?: true)
                    continue
                if (entry == metaPath) {
                    val metaS = zipFs.read(entry) { readUtf8() }
                    metaData = Json.decodeFromString<ArchiveMetaData>(metaS)
                } else {
                    extractedCount++

                    val target = dest / entry.toString().removePrefix("/")
                    FileSystem.SYSTEM.createDirectories(target.parent!!)
                    zipFs.source(entry).use { src ->
                        FileSystem.SYSTEM.sink(target).buffer().use { sink ->
                            sink.writeAll(src)
                        }
                    }
                }
            }
        }

        if (DEBUG) println("Extracted $extractedCount files")
        metaData?.executables?.forEach {
            val fullpath = "$dest/$it"
            if (DEBUG) println("Setting permissions to $fullpath")
            Files.addPermissions(
                fullpath,
                OTHERS_EXECUTE,
                GROUP_EXECUTE,
                OWNER_EXECUTE
            )
        }
    }
}

