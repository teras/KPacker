/*
 * SPDX-License-Identifier: BSD-3-Clause
 * Copyright (c) 2025, KPacker Contributors
 */


package onl.ycode.kpacker.packers

import onl.ycode.kpacker.utils.PackageDownloader

object MacArm64Configurator : MacBaseConfigurator() {
    override suspend fun fetchDist() = PackageDownloader.fetchMacArm64()
}
