/*
 * SPDX-License-Identifier: BSD-3-Clause
 * Copyright (c) 2025, KPacker Contributors
 */

package onl.ycode.kpacker

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import onl.ycode.argos.parse
import onl.ycode.koren.System
import onl.ycode.kpacker.packers.MacX64Configurator
import onl.ycode.kpacker.packers.packApp
import onl.ycode.kpacker.utils.TempFolderRegistry

fun main(cmdargs: Array<String>) {
    val args = Args().parse(cmdargs) ?: return
    val app = Application(args)

    // Parse removeTemp boolean from string
    val shouldRemoveTemp = args.removeTemp.lowercase() == "true"

    // Register exit hook for temporary folder cleanup if enabled
    if (shouldRemoveTemp) {
        System.addExitHook {
            if (DEBUG) println("Exit hook: Cleaning up temporary folders...")
            TempFolderRegistry.cleanupAll()
        }
    }

    try {
        runBlocking {
            args.target.map {
                async { packApp(args.out, app, it.configurator) }
            }.awaitAll()
        }
    } finally {
        // Cleanup all remaining temporary directories (if removeTemp is enabled)
        if (shouldRemoveTemp) {
            TempFolderRegistry.cleanupAll()
        }
    }
}