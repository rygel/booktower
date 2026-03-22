package org.booktower.services

import org.jdbi.v3.core.Jdbi
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Executors

private val webhookLog = LoggerFactory.getLogger("booktower.WebhookService")

data class WebhookDto(
    val id: String,
    val userId: String,
    val name: String,
    val url: String,
    val events: List<String>,
    val enabled: Boolean,
    val createdAt: String,
)

data class CreateWebhookRequest(
    val name: String,
    val url: String,
    val events: List<String>,
)

data class WebhookEvent(
    val event: String,
    val timestamp: String,
    val data: Map<String, Any?>,
)

/**
 * Manages user-configured webhooks for event notifications.
 * Delivers events asynchronously via HTTP POST with JSON payload.
 *
 * Supported events:
 * - `book.added` — new book created
 * - `book.deleted` — book removed
 * - `book.finished` — reading status set to FINISHED
 * - `download.complete` — background download finished
 * - `library.scanned` — library scan completed
 */
class WebhookService(
    private val jdbi: Jdbi,
) {
    private val executor =
        Executors.newFixedThreadPool(2) { r ->
            Thread(r, "webhook-delivery").also {
                it.isDaemon = true
                it.priority = Thread.MIN_PRIORITY
            }
        }

    fun list(userId: UUID): List<WebhookDto> =
        jdbi.withHandle<List<WebhookDto>, Exception> { h ->
            h
                .createQuery("SELECT * FROM webhooks WHERE user_id = ? ORDER BY created_at DESC")
                .bind(0, userId.toString())
                .map { row -> mapWebhook(row) }
                .list()
        }

    fun create(
        userId: UUID,
        request: CreateWebhookRequest,
    ): WebhookDto {
        val id = UUID.randomUUID().toString()
        val now = Instant.now().toString()
        val events = request.events.joinToString(",")
        jdbi.useHandle<Exception> { h ->
            h
                .createUpdate(
                    """
                INSERT INTO webhooks (id, user_id, name, url, events, enabled, created_at)
                VALUES (?, ?, ?, ?, ?, true, ?)
                """,
                ).bind(0, id)
                .bind(1, userId.toString())
                .bind(2, request.name.take(100))
                .bind(3, request.url.take(500))
                .bind(4, events)
                .bind(5, now)
                .execute()
        }
        webhookLog.info("Webhook created: ${request.name} → ${request.url}")
        return WebhookDto(id, userId.toString(), request.name, request.url, request.events, true, now)
    }

    fun delete(
        userId: UUID,
        webhookId: String,
    ): Boolean {
        val deleted =
            jdbi.withHandle<Int, Exception> { h ->
                h
                    .createUpdate("DELETE FROM webhooks WHERE id = ? AND user_id = ?")
                    .bind(0, webhookId)
                    .bind(1, userId.toString())
                    .execute()
            }
        return deleted > 0
    }

    fun toggle(
        userId: UUID,
        webhookId: String,
        enabled: Boolean,
    ): Boolean {
        val updated =
            jdbi.withHandle<Int, Exception> { h ->
                h
                    .createUpdate("UPDATE webhooks SET enabled = ? WHERE id = ? AND user_id = ?")
                    .bind(0, enabled)
                    .bind(1, webhookId)
                    .bind(2, userId.toString())
                    .execute()
            }
        return updated > 0
    }

    /**
     * Fires an event to all enabled webhooks for [userId] that subscribe to [eventType].
     * Delivery is async — returns immediately.
     */
    fun fire(
        userId: UUID,
        eventType: String,
        data: Map<String, Any?> = emptyMap(),
    ) {
        val webhooks =
            jdbi.withHandle<List<WebhookDto>, Exception> { h ->
                h
                    .createQuery("SELECT * FROM webhooks WHERE user_id = ? AND enabled = true")
                    .bind(0, userId.toString())
                    .map { row -> mapWebhook(row) }
                    .list()
            }
        val event =
            WebhookEvent(
                event = eventType,
                timestamp = Instant.now().toString(),
                data = data,
            )
        val payload =
            org.booktower.config.Json.mapper
                .writeValueAsString(event)

        for (webhook in webhooks) {
            if (eventType !in webhook.events) continue
            executor.submit { deliver(webhook, payload) }
        }
    }

    private fun deliver(
        webhook: WebhookDto,
        payload: String,
    ) {
        try {
            val conn =
                java.net
                    .URI(webhook.url)
                    .toURL()
                    .openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("User-Agent", "BookTower-Webhook/1.0")
            conn.setRequestProperty("X-BookTower-Event", webhook.events.firstOrNull() ?: "unknown")
            conn.doOutput = true
            conn.outputStream.use { it.write(payload.toByteArray()) }
            val code = conn.responseCode
            if (code in 200..299) {
                webhookLog.debug("Webhook delivered to ${webhook.url}: HTTP $code")
            } else {
                webhookLog.warn("Webhook delivery failed to ${webhook.url}: HTTP $code")
            }
        } catch (e: Exception) {
            webhookLog.warn("Webhook delivery error for ${webhook.url}: ${e.message}")
        }
    }

    private fun mapWebhook(row: org.jdbi.v3.core.result.RowView): WebhookDto =
        WebhookDto(
            id = row.getColumn("id", String::class.java),
            userId = row.getColumn("user_id", String::class.java),
            name = row.getColumn("name", String::class.java) ?: "",
            url = row.getColumn("url", String::class.java) ?: "",
            events = (row.getColumn("events", String::class.java) ?: "").split(",").filter { it.isNotBlank() },
            enabled = row.getColumn("enabled", Boolean::class.java) ?: true,
            createdAt = row.getColumn("created_at", String::class.java) ?: "",
        )
}
