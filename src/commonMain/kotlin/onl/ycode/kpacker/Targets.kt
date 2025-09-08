/*
 * SPDX-License-Identifier: BSD-3-Clause
 * Copyright (c) 2025, KPacker Contributors
 */


package onl.ycode.kpacker

import onl.ycode.kpacker.packers.Configurator
import onl.ycode.kpacker.packers.GenericConfigurator
import onl.ycode.kpacker.packers.LinuxArm64Configurator
import onl.ycode.kpacker.packers.LinuxX64Configurator
import onl.ycode.kpacker.packers.MacX64Configurator
import onl.ycode.kpacker.packers.WindowsX64Configurator

enum class Targets(val configurator: Configurator) {
    Generic(GenericConfigurator),
    LinuxArm64(LinuxArm64Configurator),
    LinuxX64(LinuxX64Configurator),
    MacX64(MacX64Configurator),
    WindowsX64(WindowsX64Configurator)
}