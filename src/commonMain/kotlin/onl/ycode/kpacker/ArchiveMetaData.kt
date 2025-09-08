/*
 * SPDX-License-Identifier: BSD-3-Clause
 * Copyright (c) 2025, KPacker Contributors
 */


package onl.ycode.kpacker

import kotlinx.serialization.Serializable

@Serializable
data class ArchiveMetaData(val executables: List<String>)