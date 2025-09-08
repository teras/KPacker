/*
 * SPDX-License-Identifier: BSD-3-Clause
 * Copyright (c) 2025, KPacker Contributors
 */


package onl.ycode.kpacker.packers

import onl.ycode.kpacker.utils.PackageDownloader

object LinuxX64Configurator : LinuxBaseConfigurator() {
    override suspend fun fetchDist() = PackageDownloader.fetchLinuxX64()
    override val architecture = "x86_64"
}