package org.booktower.services

import org.jdbi.v3.core.Jdbi
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

private val logger = LoggerFactory.getLogger("booktower.UserSettingsService")

class UserSettingsService(
    private val jdbi: Jdbi,
) {
    companion object {
        private const val MAX_KEY_LENGTH = 50
    }

    /** Cache for individual settings: (userId:key) → value. TTL 5 minutes. */
    private val settingsCache =
        com.github.benmanes.caffeine.cache.Caffeine
            .newBuilder()
            .maximumSize(5_000)
            .expireAfterWrite(5, java.util.concurrent.TimeUnit.MINUTES)
            .build<String, String?>()

    private fun cacheKey(
        userId: UUID,
        key: String,
    ) = "$userId:$key"

    fun getAll(userId: UUID): Map<String, String?> =
        jdbi.withHandle<Map<String, String?>, Exception> { handle ->
            handle
                .createQuery("SELECT setting_key, setting_value FROM user_settings WHERE user_id = ?")
                .bind(0, userId.toString())
                .map { row ->
                    row.getColumn("setting_key", String::class.java) to
                        row.getColumn("setting_value", String::class.java)
                }.associate { it }
        }

    fun get(
        userId: UUID,
        key: String,
    ): String? {
        val ck = cacheKey(userId, key)
        val cached = settingsCache.getIfPresent(ck)
        if (cached != null) return cached

        val value =
            jdbi.withHandle<String?, Exception> { handle ->
                handle
                    .createQuery("SELECT setting_value FROM user_settings WHERE user_id = ? AND setting_key = ?")
                    .bind(0, userId.toString())
                    .bind(1, key)
                    .mapTo(String::class.java)
                    .firstOrNull()
            }
        if (value != null) settingsCache.put(ck, value)
        return value
    }

    fun set(
        userId: UUID,
        key: String,
        value: String?,
    ): Boolean {
        validateKey(key)?.let { throw IllegalArgumentException(it) }

        val now = Instant.now().toString()
        val existing = get(userId, key)

        if (existing != null) {
            jdbi.useHandle<Exception> { handle ->
                handle
                    .createUpdate(
                        "UPDATE user_settings SET setting_value = ?, updated_at = ? WHERE user_id = ? AND setting_key = ?",
                    ).bind(0, value)
                    .bind(1, now)
                    .bind(2, userId.toString())
                    .bind(3, key)
                    .execute()
            }
        } else {
            jdbi.useHandle<Exception> { handle ->
                handle
                    .createUpdate(
                        "INSERT INTO user_settings (id, user_id, setting_key, setting_value, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
                    ).bind(0, UUID.randomUUID().toString())
                    .bind(1, userId.toString())
                    .bind(2, key)
                    .bind(3, value)
                    .bind(4, now)
                    .bind(5, now)
                    .execute()
            }
        }

        settingsCache.invalidate(cacheKey(userId, key))
        logger.info("Setting '$key' updated for user $userId")
        return true
    }

    fun delete(
        userId: UUID,
        key: String,
    ): Boolean {
        val deleted =
            jdbi.withHandle<Int, Exception> { handle ->
                handle
                    .createUpdate("DELETE FROM user_settings WHERE user_id = ? AND setting_key = ?")
                    .bind(0, userId.toString())
                    .bind(1, key)
                    .execute()
            }
        if (deleted > 0) {
            settingsCache.invalidate(cacheKey(userId, key))
            logger.info("Setting '$key' deleted for user $userId")
        }
        return deleted > 0
    }

    private fun validateKey(key: String): String? =
        when {
            key.isBlank() -> "Setting key is required"
            key.length > MAX_KEY_LENGTH -> "Setting key must be $MAX_KEY_LENGTH characters or fewer"
            !key.matches(Regex("^[a-zA-Z0-9_.-]+$")) -> "Setting key can only contain letters, numbers, underscores, dots, and dashes"
            else -> null
        }
}
