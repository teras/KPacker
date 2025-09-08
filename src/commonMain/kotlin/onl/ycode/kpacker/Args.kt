package onl.ycode.kpacker

import onl.ycode.argos.Arguments
import onl.ycode.argos.default
import onl.ycode.argos.enum
import onl.ycode.argos.required
import onl.ycode.argos.set

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
    val removeTemp by option().default("true")

    // Apple signing options
    val p12File by option()
    val p12Pass by option()
    val notaryJson by option()
    val enableSigning by option().default("false")

    // DMG template option
    val dmgTemplate by option()
}