package onl.ycode.kpacker.packers

import onl.ycode.kpacker.utils.PackageDownloader

object LinuxArm64Configurator : LinuxBaseConfigurator() {
    override suspend fun fetchDist() = PackageDownloader.fetchLinuxArm64()
    override val architecture = "aarch64"
}