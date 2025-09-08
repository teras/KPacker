/*
 * SPDX-License-Identifier: BSD-3-Clause
 * Copyright (c) 2025, KPacker Contributors
 */


package onl.ycode.kpacker.utils

import okio.Path
import okio.Path.Companion.toPath
import onl.ycode.koren.Command

interface ContainerRunner {
    val name: String
    val workingDir: Path
    fun run(command: String): Command
}

abstract class GenericRunner : ContainerRunner {
    override val workingDir = TempFolderRegistry.createTempFolder("kpacker-icns-", "-work").toPath()
}

class DockerRunner : GenericRunner() {
    override val name = "docker"

    override fun run(command: String): Command {
        println("** Running $name command: $command")
        return Command(
            name, "run", "--rm", "-t",
            "-v", "$workingDir:/work",
            "-w", "/work",
            "docker.io/teras/appimage-builder",
            "sh", "-c", command
        )
    }
}

class PodmanRunner : GenericRunner() {
    override val name = "podman"

    override fun run(command: String): Command {
        println("** Running $name command: $command")
        return Command(
            name, "run", "--rm", "-t",
            "-v", "$workingDir:/work",
            "-w", "/work",
            "docker.io/teras/appimage-builder",
            "sh", "-c", command
        )
    }
}

object ContainerFactory {
    fun createRunner(): ContainerRunner {
        // Try to find available container runtime in PATH and common locations
        return when {
            isCommandAvailable("docker") -> DockerRunner()
            isCommandAvailable("podman") -> PodmanRunner()
            else -> throw IllegalArgumentException("No container runtime (Docker or Podman) found in PATH")
        }
    }

    private fun isCommandAvailable(command: String): Boolean {
        // First try 'which' command
        try {
            val whichCmd = Command("which", command)
            if (whichCmd.exec().waitFor() == 0) {
                return true
            }
        } catch (e: Exception) {
            // which command failed, continue to direct path checks
        }

        // Fallback: try common installation paths directly
        val commonPaths = listOf(
            "/usr/bin/$command",
            "/usr/local/bin/$command",
            "/opt/bin/$command",
            "/bin/$command"
        )

        for (path in commonPaths) {
            try {
                val testCmd = Command(path, "--version")
                if (testCmd.exec().waitFor() == 0) {
                    return true
                }
            } catch (e: Exception) {
                // Continue trying other paths
            }
        }

        return false
    }
}