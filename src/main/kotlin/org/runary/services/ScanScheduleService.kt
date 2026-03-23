package org.runary.services

import org.jdbi.v3.core.Jdbi
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private val logger = LoggerFactory.getLogger("runary.ScanScheduleService")

class ScanScheduleService(
    private val jdbi: Jdbi,
    private val libraryService: LibraryService,
    val intervalMinutes: Long,
) {
    private val executor =
        Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "scan-scheduler").also { it.isDaemon = true }
        }

    fun start() {
        if (intervalMinutes <= 0) {
            logger.info("Auto-scan disabled (RUNARY_AUTO_SCAN_MINUTES=0)")
            return
        }
        executor.scheduleWithFixedDelay(::scanAll, intervalMinutes, intervalMinutes, TimeUnit.MINUTES)
        logger.info("Auto-scan scheduled every $intervalMinutes minutes")
        Runtime.getRuntime().addShutdownHook(Thread { stop() })
    }

    fun stop() {
        executor.shutdownNow()
        logger.info("Auto-scan scheduler stopped")
    }

    private fun scanAll() {
        logger.info("Auto-scan triggered")
        val pairs =
            try {
                jdbi.withHandle<List<Pair<UUID, UUID>>, Exception> { handle ->
                    handle
                        .createQuery("SELECT user_id, id FROM libraries")
                        .map { rs, _ ->
                            Pair(
                                UUID.fromString(rs.getString("user_id")),
                                UUID.fromString(rs.getString("id")),
                            )
                        }.list()
                }
            } catch (e: Exception) {
                logger.error("Auto-scan: failed to load libraries", e)
                return
            }

        var added = 0
        var skipped = 0
        for ((userId, libId) in pairs) {
            runCatching { libraryService.scanLibrary(userId, libId) }
                .onSuccess { result ->
                    added += result.added
                    skipped += result.skipped
                }.onFailure { e -> logger.warn("Auto-scan failed for library $libId: ${e.message}") }
        }
        logger.info("Auto-scan complete: +$added added, $skipped skipped across ${pairs.size} libraries")
    }
}
