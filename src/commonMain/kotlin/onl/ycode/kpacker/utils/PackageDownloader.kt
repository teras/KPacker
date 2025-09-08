/*
 * SPDX-License-Identifier: BSD-3-Clause
 * Copyright (c) 2025, KPacker Contributors
 */


package onl.ycode.kpacker.utils

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.utils.io.*
import okio.*
import okio.Path.Companion.toPath
import onl.ycode.kpacker.APP_ICON_URL
import onl.ycode.kpacker.DEBUG
import onl.ycode.kpacker.LINUX_ARM64_URL
import onl.ycode.kpacker.LINUX_X64_URL
import onl.ycode.kpacker.MACOS_X64_URL
import onl.ycode.kpacker.PACKAGE_LOCATION
import onl.ycode.kpacker.WINDOWS_X64_URL

object PackageDownloader {
    val fs = FileSystem.SYSTEM

    val client = HttpClient {}

    private suspend fun fetch(url: String, location: String): Path {
        val locPath = location.toPath()
        if (fs.exists(locPath) && fs.metadata(locPath).isRegularFile)
            return locPath

        fs.deleteRecursively(locPath)
        fs.createDirectories(locPath.parent!!)

        val ch = client.get(url).bodyAsChannel()
        fs.sink(locPath).buffer().use { sink ->
            val buf = ByteArray(8192)
            while (!ch.isClosedForRead) {
                val n = ch.readAvailable(buf, 0, buf.size)
                if (n <= 0) break
                sink.write(buf, 0, n)
            }
            sink.flush()
        }
        return locPath
    }

    suspend fun fetchLinuxArm64() = fetch(
        LINUX_ARM64_URL,
        PACKAGE_LOCATION + "/" + LINUX_ARM64_URL.substringAfterLast('/')
    )

    suspend fun fetchLinuxX64() = fetch(
        LINUX_X64_URL,
        PACKAGE_LOCATION + "/" + LINUX_X64_URL.substringAfterLast('/')
    )

    suspend fun fetchMacX64() = fetch(
        MACOS_X64_URL,
        PACKAGE_LOCATION + "/" + MACOS_X64_URL.substringAfterLast('/')
    )

    suspend fun fetchWindowsX64() = fetch(
        WINDOWS_X64_URL,
        PACKAGE_LOCATION + "/" + WINDOWS_X64_URL.substringAfterLast('/')
    )

    suspend fun fetchAppIcon(): Path {
        val iconPath = fetch(
            APP_ICON_URL,
            PACKAGE_LOCATION + "/" + APP_ICON_URL.substringAfterLast('/')
        )
        if (DEBUG) println("Downloaded default icon to: $iconPath")
        return iconPath
    }

}