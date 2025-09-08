package onl.ycode.kpacker.utils

import onl.ycode.koren.Command

interface ContainerRunner {
    val name: String
    fun run(vararg args: String): Command
}

class DockerRunner : ContainerRunner {
    override val name = "docker"

    override fun run(vararg args: String): Command {
        return Command(name, *args)
    }
}

class PodmanRunner : ContainerRunner {
    override val name = "podman"

    override fun run(vararg args: String): Command {
        return Command(name, *args)
    }
}

object ContainerFactory {
    fun createRunner(): ContainerRunner? {
        // Try to find available container runtime in PATH and common locations
        return when {
            isCommandAvailable("podman") -> PodmanRunner()
            isCommandAvailable("docker") -> DockerRunner()
            else -> null
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