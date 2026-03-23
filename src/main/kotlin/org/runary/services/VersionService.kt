package org.runary.services

import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("runary.VersionService")

data class VersionInfo(
    val version: String,
    val name: String,
    val jvm: String,
    val kotlinVersion: String,
    val gitCommit: String? = null,
    val gitBranch: String? = null,
) {
    /** Display version: "0.5.2" for releases, "dev (abc1234 on develop)" for dev mode. */
    val display: String
        get() =
            if (version == "dev" && gitCommit != null) {
                "dev (${gitCommit.take(7)}${if (gitBranch != null) " on $gitBranch" else ""})"
            } else {
                version
            }
}

object VersionService {
    val info: VersionInfo by lazy { load() }

    private fun load(): VersionInfo {
        val pkg = VersionService::class.java.`package`
        val version =
            pkg?.implementationVersion
                ?: System.getProperty("app.version")
                ?: "dev"
        val name =
            pkg?.implementationTitle
                ?: System.getProperty("app.name")
                ?: "runary"
        val jvm = "${System.getProperty("java.version")} (${System.getProperty("java.vendor")})"
        val kotlin = KotlinVersion.CURRENT.toString()
        val gitCommit = if (version == "dev") gitCommand("git", "rev-parse", "HEAD") else null
        val gitBranch = if (version == "dev") gitCommand("git", "rev-parse", "--abbrev-ref", "HEAD") else null
        logger.info("Runary $version${if (gitCommit != null) " (${gitCommit.take(7)} on $gitBranch)" else ""} starting on JVM $jvm")
        return VersionInfo(version = version, name = name, jvm = jvm, kotlinVersion = kotlin, gitCommit = gitCommit, gitBranch = gitBranch)
    }

    private fun gitCommand(vararg args: String): String? =
        try {
            val process = ProcessBuilder(*args).redirectErrorStream(true).start()
            val output =
                process.inputStream
                    .bufferedReader()
                    .readText()
                    .trim()
            if (process.waitFor() == 0 && output.isNotBlank()) output else null
        } catch (_: Exception) {
            null
        }
}
