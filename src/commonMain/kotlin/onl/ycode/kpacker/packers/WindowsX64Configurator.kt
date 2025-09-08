package onl.ycode.kpacker.packers

import onl.ycode.kpacker.utils.PackageDownloader

object WindowsX64Configurator : WindowsBaseConfigurator() {
    override suspend fun fetchDist() = PackageDownloader.fetchWindowsX64()
    override val is64Bit = true
    override val architecture = "x64"
}