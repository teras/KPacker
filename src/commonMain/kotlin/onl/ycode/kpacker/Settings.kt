@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package onl.ycode.kpacker

import okio.Path.Companion.toPath
import onl.ycode.koren.System

const val DEBUG = true

expect val IS_WINDOWS: Boolean
expect val EXE_EXTENSION: String
val PACKAGE_LOCATION =
    if (IS_WINDOWS) "${System.getenv("USERPROFILE")}/.cache/kpacker/jres"
    else "${System.getenv("HOME")}/.cache/kpacker/jres"
val PATHS =
    (if (IS_WINDOWS) System.getenv("PATH")?.split(";")
    else System.getenv("PATH")?.split(":"))?.map { it.toPath() } ?: emptyList()

const val LINUX_ARM64_URL = "https://github.com/teras/KPacker/releases/download/filerepo/linux_arm64_template.zip"
const val LINUX_X64_URL = "https://github.com/teras/KPacker/releases/download/filerepo/linux_x64_template.zip"
const val MACOS_X64_URL = "https://github.com/teras/KPacker/releases/download/filerepo/mac_x64_template.zip"
const val WINDOWS_X64_URL = "https://github.com/teras/KPacker/releases/download/filerepo/windows_x64_template.zip"
const val APP_ICON_URL = "https://github.com/teras/KPacker/releases/download/filerepo/default_icon.png"