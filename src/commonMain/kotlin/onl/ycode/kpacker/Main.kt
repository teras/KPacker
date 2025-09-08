package onl.ycode.kpacker


import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM
import okio.openZip


fun main(args: Array<String>) {
    val archiveName =
        if (args.isEmpty()) "/home/teras/Works/Development/Containers/jre/templates/linux_arm64_template.zip"
        else args[0]

    val client = HttpClient {
    }

    val json = runBlocking {
        client.get("https://svatky.adresa.info/json").body<String>()
    }
    Json.decodeFromString<List<JData>>(json).forEach { println(it) }


    val archive = FileSystem.SYSTEM.openZip(archiveName.toPath())

    val metaS = archive.read("/.metadata".toPath()) { readUtf8() }
    val meta = Json.decodeFromString<ArchiveMetaData>(metaS)

    meta.executables.forEach { println(it) }
}
