@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package onl.ycode.kpacker

import onl.ycode.korr.System

expect class NativeConfig {
    val packageLocation: String
}

internal val UNIX_PACKAGE_LOCATION = System.getenv("HOME") + "/.cache/jubpak/jres"
internal val WINDOWS_PACKAGE_LOCATION = System.getenv("USERPROFILE") + "/.cache/jubpak/jres"

class Config {
}

