package org.booktower.services

import org.jdbi.v3.core.Jdbi
import java.io.File

data class HealthStatus(
    val status: String,
    val version: String,
    val database: String,
    val diskFreeBytes: Long,
    val diskTotalBytes: Long,
    val diskUsedPercent: Int,
    val uptime: String,
    val jvmMemoryUsedMb: Int,
    val jvmMemoryMaxMb: Int,
)

/**
 * Health check for Docker/Kubernetes liveness and readiness probes.
 * Returns system status without requiring authentication.
 */
class HealthService(
    private val jdbi: Jdbi,
) {
    private val startTime = System.currentTimeMillis()

    fun check(): HealthStatus {
        val dbStatus =
            try {
                jdbi.withHandle<Boolean, Exception> { h ->
                    h.createQuery("SELECT 1").mapTo(Int::class.javaObjectType).one() == 1
                }
                "ok"
            } catch (e: Exception) {
                "error: ${e.message?.take(100)}"
            }

        val dataDir = File("./data")
        val freeSpace = dataDir.freeSpace
        val totalSpace = dataDir.totalSpace
        val usedPercent = if (totalSpace > 0) ((totalSpace - freeSpace) * 100 / totalSpace).toInt() else 0

        val uptimeMs = System.currentTimeMillis() - startTime
        val uptimeHours = uptimeMs / 3_600_000
        val uptimeMinutes = (uptimeMs % 3_600_000) / 60_000
        val uptimeStr = "${uptimeHours}h ${uptimeMinutes}m"

        val runtime = Runtime.getRuntime()
        val usedMb = ((runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)).toInt()
        val maxMb = (runtime.maxMemory() / (1024 * 1024)).toInt()

        return HealthStatus(
            status = if (dbStatus == "ok") "healthy" else "degraded",
            version = VersionService.info.version,
            database = dbStatus,
            diskFreeBytes = freeSpace,
            diskTotalBytes = totalSpace,
            diskUsedPercent = usedPercent,
            uptime = uptimeStr,
            jvmMemoryUsedMb = usedMb,
            jvmMemoryMaxMb = maxMb,
        )
    }
}
