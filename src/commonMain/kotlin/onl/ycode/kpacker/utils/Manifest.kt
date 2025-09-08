/*
 * SPDX-License-Identifier: BSD-3-Clause
 * Copyright (c) 2025, KPacker Contributors
 */


package onl.ycode.kpacker.utils

import okio.*
import okio.Path.Companion.toPath

object Manifest {

    fun load(path: Path): Map<String, String> {
        val manifestData = FileSystem.SYSTEM.openZip(path).use { zipFs ->
            zipFs.read("/META-INF/MANIFEST.MF".toPath()) { readUtf8() }
        }
        return parse(manifestData)
    }

    fun parse(manifest: String): Map<String, String> {
        val result = linkedMapOf<String, String>()   // preserves order
        var currentKey: String? = null
        val value = StringBuilder()

        fun flush() {
            if (currentKey != null) result[currentKey!!] = value.toString()
            currentKey = null
            value.setLength(0)
        }

        for (raw in manifest.lineSequence()) {
            val line = raw.trimEnd('\r')             // tolerate CRLF
            if (line.isEmpty()) {                    // end of main section
                flush()
                break
            }
            if (line.startsWith(" ")) {              // continuation line
                if (currentKey != null) value.append(line.drop(1))
                continue
            }
            flush()
            val i = line.indexOf(':')
            if (i <= 0) continue                     // ignore malformed
            currentKey = line.substring(0, i)
            val start = if (i + 1 < line.length && line[i + 1] == ' ') i + 2 else i + 1
            value.append(line.substring(start))
        }
        flush()
        return result
    }
}