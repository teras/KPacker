/*
 * SPDX-License-Identifier: BSD-3-Clause
 * Copyright (c) 2025, KPacker Contributors
 */


package onl.ycode.kpacker

import onl.ycode.argos.Arguments
import onl.ycode.argos.bool
import onl.ycode.argos.default
import onl.ycode.argos.enum
import onl.ycode.argos.negatable
import onl.ycode.argos.required
import onl.ycode.argos.set
import onl.ycode.argos.terminal.ANSITerminal
import onl.ycode.argos.terminal.PlainTerminal

class Args : Arguments(
    appName = "kpacker",
    appDescription = "Kotlin Application Packer"
) {
    val help by help()
    val source by option().required()
    val name by option().required()
    val version by option().default("1.0.0")
    val mainjar by option()
    val target by option().enum<Targets>().set().required()
    val out by option().required()
    val icon by option()
    val installIcon by option()
    val documentIcon by option().requireIfAnyPresent(::documentExtensions).help("icon file for document types (required when --document-extensions is specified)")
    val documentExtensions by option().help("comma-separated list of file extensions to associate with the app (e.g., \"txt,md,pdf\")")
    val documentName by option().help("human-readable name for the document type (defaults to \"{app-name} Document\")")
    val removeTemp by option().default("true")

    // Apple signing options
    val p12File by option()
    val p12Pass by option()
    val notaryJson by option()
    val enableSigning by option().default("false")

    val ansi by option().bool().default(true).negatable().onValue { if (!it) this.setTerminal(PlainTerminal()) }

    // DMG template option
    val dmgTemplate by option()

    // DMG creation options
    val dmgCompress by option().bool().default(true).negatable()
    val skipDmg by option().bool().default(false)
}