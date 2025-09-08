/*
 * SPDX-License-Identifier: BSD-3-Clause
 * Copyright (c) 2025, KPacker Contributors
 */


package onl.ycode.kpacker

actual val IS_WINDOWS = System.getProperty("os.name").lowercase().contains("win")

actual val EXE_EXTENSION = if (IS_WINDOWS) ".exe" else ""
