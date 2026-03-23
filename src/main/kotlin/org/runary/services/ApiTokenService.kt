package org.runary.services

import org.runary.models.ApiTokenDto
import org.runary.models.CreatedApiTokenResponse
import org.jdbi.v3.core.Jdbi
import org.slf4j.LoggerFactory
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.UUID

private val logger = LoggerFactory.getLogger("runary.ApiTokenService")
private val tokenRng = SecureRandom()

class ApiTokenService(
    private val jdbi: Jdbi,
) {
    fun createToken(
        userId: UUID,
        name: String,
    ): CreatedApiTokenResponse {
        val rawToken = generateToken()
        val tokenHash = hashToken(rawToken)
        val now = Instant.now()
        val id = UUID.randomUUID().toString()

        jdbi.useHandle<Exception> { handle ->
            handle
                .createUpdate(
                    "INSERT INTO api_tokens (id, user_id, name, token_hash, created_at) VALUES (?,?,?,?,?)",
                ).bind(0, id)
                .bind(1, userId.toString())
                .bind(2, name)
                .bind(3, tokenHash)
                .bind(4, now.toString())
                .execute()
        }

        logger.info("API token created for user $userId: $name")
        return CreatedApiTokenResponse(id = id, name = name, token = rawToken, createdAt = now.toString())
    }

    fun listTokens(userId: UUID): List<ApiTokenDto> =
        jdbi.withHandle<List<ApiTokenDto>, Exception> { handle ->
            handle
                .createQuery(
                    "SELECT id, name, created_at, last_used_at FROM api_tokens WHERE user_id = ? ORDER BY created_at DESC",
                ).bind(0, userId.toString())
                .map { r ->
                    ApiTokenDto(
                        id = r.getColumn("id", String::class.java),
                        name = r.getColumn("name", String::class.java),
                        createdAt = r.getColumn("created_at", String::class.java),
                        lastUsedAt = r.getColumn("last_used_at", String::class.java),
                    )
                }.list()
        }

    fun revokeToken(
        userId: UUID,
        tokenId: UUID,
    ): Boolean {
        val deleted =
            jdbi.withHandle<Int, Exception> { handle ->
                handle
                    .createUpdate("DELETE FROM api_tokens WHERE id = ? AND user_id = ?")
                    .bind(0, tokenId.toString())
                    .bind(1, userId.toString())
                    .execute()
            }
        if (deleted > 0) logger.info("API token $tokenId revoked for user $userId")
        return deleted > 0
    }

    /** Validates a raw Bearer token. Returns the owning userId, or null if invalid. */
    fun validateToken(rawToken: String): UUID? {
        val tokenHash = hashToken(rawToken)
        val row =
            jdbi.withHandle<Pair<String, String>?, Exception> { handle ->
                handle
                    .createQuery("SELECT id, user_id FROM api_tokens WHERE token_hash = ?")
                    .bind(0, tokenHash)
                    .map { r ->
                        Pair(
                            r.getColumn("id", String::class.java),
                            r.getColumn("user_id", String::class.java),
                        )
                    }.firstOrNull()
            } ?: return null

        val (tokenId, userId) = row
        // Update last_used_at in background (best-effort)
        try {
            jdbi.useHandle<Exception> { handle ->
                handle
                    .createUpdate("UPDATE api_tokens SET last_used_at = ? WHERE id = ?")
                    .bind(0, Instant.now().toString())
                    .bind(1, tokenId)
                    .execute()
            }
        } catch (_: Exception) {
            // non-critical
        }

        return try {
            UUID.fromString(userId)
        } catch (_: Exception) {
            null
        }
    }

    private fun generateToken(): String {
        val bytes = ByteArray(32)
        tokenRng.nextBytes(bytes)
        return "bt_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun hashToken(raw: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        return digest.digest(raw.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
