package org.runary.services

import org.jdbi.v3.core.Jdbi
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

private val logger = LoggerFactory.getLogger("runary.TelemetryService")

private const val TELEMETRY_OPT_IN_KEY = "telemetry.opt_in"

data class TelemetryEventDto(
    val id: String,
    val eventType: String,
    val payload: String?,
    val recordedAt: String,
)

data class TelemetryStatsDto(
    val totalEvents: Int,
    val byType: Map<String, Int>,
    val optedInUsers: Int,
)

class TelemetryService(
    private val jdbi: Jdbi,
    private val userSettingsService: UserSettingsService,
) {
    fun isOptedIn(userId: UUID): Boolean = userSettingsService.get(userId, TELEMETRY_OPT_IN_KEY) == "true"

    fun optIn(userId: UUID) {
        userSettingsService.set(userId, TELEMETRY_OPT_IN_KEY, "true")
        logger.info("User $userId opted into telemetry")
    }

    fun optOut(userId: UUID) {
        userSettingsService.set(userId, TELEMETRY_OPT_IN_KEY, "false")
        logger.info("User $userId opted out of telemetry")
    }

    /**
     * Record an anonymous event if the user has opted in.
     * No user ID is stored in the event — events are anonymous by design.
     */
    fun record(
        userId: UUID,
        eventType: String,
        payload: String? = null,
    ) {
        if (!isOptedIn(userId)) return
        val id = UUID.randomUUID().toString()
        val now = Instant.now().toString()
        try {
            jdbi.useHandle<Exception> { h ->
                h
                    .createUpdate(
                        "INSERT INTO telemetry_events (id, event_type, payload, recorded_at) VALUES (?, ?, ?, ?)",
                    ).bind(0, id)
                    .bind(1, eventType)
                    .bind(2, payload)
                    .bind(3, now)
                    .execute()
            }
        } catch (e: Exception) {
            logger.debug("Failed to record telemetry event: ${e.message}")
        }
    }

    fun getStats(): TelemetryStatsDto {
        val byType =
            jdbi.withHandle<Map<String, Int>, Exception> { h ->
                h
                    .createQuery("SELECT event_type, COUNT(*) as cnt FROM telemetry_events GROUP BY event_type")
                    .map { row ->
                        row.getColumn("event_type", String::class.java) to
                            ((row.getColumn("cnt", java.lang.Integer::class.java) as? Int) ?: 0)
                    }.associate { it }
            }
        val total = byType.values.sum()
        val optedIn = countOptedInUsers()
        return TelemetryStatsDto(totalEvents = total, byType = byType, optedInUsers = optedIn)
    }

    private fun countOptedInUsers(): Int =
        jdbi.withHandle<Int, Exception> { h ->
            h
                .createQuery(
                    "SELECT COUNT(*) FROM user_settings WHERE setting_key = ? AND setting_value = 'true'",
                ).bind(0, TELEMETRY_OPT_IN_KEY)
                .mapTo(java.lang.Integer::class.java)
                .first()
                ?.toInt() ?: 0
        }
}
