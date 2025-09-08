package onl.ycode.kpacker.packers

import okio.Path
import onl.ycode.kpacker.Application

interface Configurator {
    suspend fun fetchDist(): Path
    fun baseDirName(name: String): String
    fun binLocation(name: String): String
    val appLocation: String
    suspend fun postProcess(targetDir: String, app: Application) {}
}