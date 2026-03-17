package org.booktower.services

import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("booktower.VersionService")

data class VersionInfo(
    val version: String,
    val name: String,
    val jvm: String,
    val kotlinVersion: String,
)

object VersionService {

    val info: VersionInfo by lazy { load() }

    private fun load(): VersionInfo {
        val pkg = VersionService::class.java.`package`
        val version = pkg?.implementationVersion
            ?: System.getProperty("app.version")
            ?: "dev"
        val name = pkg?.implementationTitle
            ?: System.getProperty("app.name")
            ?: "booktower"
        val jvm = "${System.getProperty("java.version")} (${System.getProperty("java.vendor")})"
        val kotlin = KotlinVersion.CURRENT.toString()
        logger.info("BookTower $version starting on JVM $jvm")
        return VersionInfo(version = version, name = name, jvm = jvm, kotlinVersion = kotlin)
    }
}
