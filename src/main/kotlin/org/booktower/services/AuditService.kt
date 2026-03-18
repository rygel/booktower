package org.booktower.services

import org.jdbi.v3.core.Jdbi
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

private val logger = LoggerFactory.getLogger("booktower.AuditService")

data class AuditEntry(
    val id: String,
    val actorId: String,
    val actorName: String,
    val action: String,
    val targetType: String?,
    val targetId: String?,
    val detail: String?,
    val ipAddress: String?,
    val countryCode: String?,
    val countryName: String?,
    val city: String?,
    val occurredAt: String,
)

class AuditService(
    private val jdbi: Jdbi,
    private val geoIpService: GeoIpService? = null,
) {
    fun record(
        actorId: UUID,
        actorName: String,
        action: String,
        targetType: String? = null,
        targetId: String? = null,
        detail: String? = null,
        ipAddress: String? = null,
    ) {
        val geo = if (ipAddress != null) geoIpService?.lookup(ipAddress) else null
        val id = UUID.randomUUID().toString()
        try {
            jdbi.useHandle<Exception> { h ->
                h
                    .createUpdate(
                        """INSERT INTO audit_log (id, actor_id, actor_name, action, target_type, target_id, detail, ip_address, country_code, country_name, city, occurred_at)
                       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                    ).bind(0, id)
                    .bind(1, actorId.toString())
                    .bind(2, actorName)
                    .bind(3, action)
                    .bind(4, targetType)
                    .bind(5, targetId)
                    .bind(6, detail)
                    .bind(7, ipAddress)
                    .bind(8, geo?.countryCode)
                    .bind(9, geo?.countryName)
                    .bind(10, geo?.city)
                    .bind(11, Instant.now().toString())
                    .execute()
            }
        } catch (e: Exception) {
            logger.error("Failed to write audit log entry for action=$action actor=$actorId", e)
        }
    }

    fun listRecent(limit: Int = 200): List<AuditEntry> =
        jdbi.withHandle<List<AuditEntry>, Exception> { h ->
            h
                .createQuery(
                    """SELECT id, actor_id, actor_name, action, target_type, target_id, detail, ip_address, country_code, country_name, city, occurred_at
                   FROM audit_log ORDER BY occurred_at DESC LIMIT ?""",
                ).bind(0, limit)
                .map { row ->
                    AuditEntry(
                        id = row.getColumn("id", String::class.java),
                        actorId = row.getColumn("actor_id", String::class.java),
                        actorName = row.getColumn("actor_name", String::class.java),
                        action = row.getColumn("action", String::class.java),
                        targetType = row.getColumn("target_type", String::class.java),
                        targetId = row.getColumn("target_id", String::class.java),
                        detail = row.getColumn("detail", String::class.java),
                        ipAddress = row.getColumn("ip_address", String::class.java),
                        countryCode = row.getColumn("country_code", String::class.java),
                        countryName = row.getColumn("country_name", String::class.java),
                        city = row.getColumn("city", String::class.java),
                        occurredAt = row.getColumn("occurred_at", String::class.java) ?: "",
                    )
                }.list()
        }

    fun listForUser(
        userId: UUID,
        limit: Int = 200,
    ): List<AuditEntry> =
        jdbi.withHandle<List<AuditEntry>, Exception> { h ->
            h
                .createQuery(
                    """SELECT id, actor_id, actor_name, action, target_type, target_id, detail, ip_address, country_code, country_name, city, occurred_at
                   FROM audit_log WHERE actor_id = ? ORDER BY occurred_at DESC LIMIT ?""",
                ).bind(0, userId.toString())
                .bind(1, limit)
                .map { row ->
                    AuditEntry(
                        id = row.getColumn("id", String::class.java),
                        actorId = row.getColumn("actor_id", String::class.java),
                        actorName = row.getColumn("actor_name", String::class.java),
                        action = row.getColumn("action", String::class.java),
                        targetType = row.getColumn("target_type", String::class.java),
                        targetId = row.getColumn("target_id", String::class.java),
                        detail = row.getColumn("detail", String::class.java),
                        ipAddress = row.getColumn("ip_address", String::class.java),
                        countryCode = row.getColumn("country_code", String::class.java),
                        countryName = row.getColumn("country_name", String::class.java),
                        city = row.getColumn("city", String::class.java),
                        occurredAt = row.getColumn("occurred_at", String::class.java) ?: "",
                    )
                }.list()
        }
}
