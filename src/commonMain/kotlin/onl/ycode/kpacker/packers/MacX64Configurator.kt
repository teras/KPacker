/*
 * SPDX-License-Identifier: BSD-3-Clause
 * Copyright (c) 2025, KPacker Contributors
 */


package onl.ycode.kpacker.packers

import onl.ycode.kpacker.utils.PackageDownloader

object MacX64Configurator : MacBaseConfigurator() {
    override val architecture = "x86_64"
    override suspend fun fetchDist() = PackageDownloader.fetchMacX64()
}
