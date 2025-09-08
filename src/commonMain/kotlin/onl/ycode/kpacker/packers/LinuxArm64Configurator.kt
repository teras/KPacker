/*
 * SPDX-License-Identifier: BSD-3-Clause
 * Copyright (c) 2025, KPacker Contributors
 */


package onl.ycode.kpacker.packers

import onl.ycode.kpacker.utils.PackageDownloader

object LinuxArm64Configurator : LinuxBaseConfigurator() {
    override suspend fun fetchDist() = PackageDownloader.fetchLinuxArm64()
    override val architecture = "aarch64"
}