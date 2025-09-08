@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package onl.ycode.kpacker

actual class NativeConfig {
    actual val packageLocation =
        if (System.getProperty("os.name").lowercase().contains("win")) UNIX_PACKAGE_LOCATION else UNIX_PACKAGE_LOCATION
}