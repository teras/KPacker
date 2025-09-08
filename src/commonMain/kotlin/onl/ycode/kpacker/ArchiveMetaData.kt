package onl.ycode.kpacker

import kotlinx.serialization.Serializable

@Serializable
data class ArchiveMetaData(val executables: List<String>)