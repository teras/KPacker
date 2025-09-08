package onl.ycode.kpacker.utils

import okio.FileSystem
import okio.SYSTEM
import onl.ycode.kpacker.EXE_EXTENSION
import onl.ycode.kpacker.PATHS

enum class Externals {
    PNG2ICNS,
    CONVERT,
    KOKO;

    private val exe = name.lowercase() + EXE_EXTENSION

    val path by lazy {
        PATHS.map { it.resolve(exe) }.firstOrNull { FileSystem.SYSTEM.exists(it) }
            ?: throw Exception("External tool $exe not found in PATH")
    }
}