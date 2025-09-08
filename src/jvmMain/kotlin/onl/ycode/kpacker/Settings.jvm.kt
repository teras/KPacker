package onl.ycode.kpacker

actual val IS_WINDOWS = System.getProperty("os.name").lowercase().contains("win")

actual val EXE_EXTENSION = if (IS_WINDOWS) ".exe" else ""
