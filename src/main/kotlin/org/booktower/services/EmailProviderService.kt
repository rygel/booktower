package org.booktower.services

import org.jdbi.v3.core.Jdbi
import java.time.Instant
import java.util.UUID

data class EmailProviderDto(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val username: String,
    val fromAddress: String,
    val useTls: Boolean,
    val isDefault: Boolean,
    val createdAt: String,
    val updatedAt: String,
)

data class CreateEmailProviderRequest(
    val name: String,
    val host: String,
    val port: Int = 587,
    val username: String,
    val password: String,
    val fromAddress: String,
    val useTls: Boolean = true,
    val isDefault: Boolean = false,
)

data class UpdateEmailProviderRequest(
    val name: String? = null,
    val host: String? = null,
    val port: Int? = null,
    val username: String? = null,
    val password: String? = null,
    val fromAddress: String? = null,
    val useTls: Boolean? = null,
    val isDefault: Boolean? = null,
)

class EmailProviderService(private val jdbi: Jdbi) {

    fun list(): List<EmailProviderDto> =
        jdbi.withHandle<List<EmailProviderDto>, Exception> { h ->
            h.createQuery(
                "SELECT id, name, host, port, username, from_address, use_tls, is_default, created_at, updated_at FROM email_providers ORDER BY is_default DESC, name",
            )
                .map { row -> mapRow(row) }
                .list()
        }

    fun get(id: String): EmailProviderDto? =
        jdbi.withHandle<EmailProviderDto?, Exception> { h ->
            h.createQuery(
                "SELECT id, name, host, port, username, from_address, use_tls, is_default, created_at, updated_at FROM email_providers WHERE id = ?",
            )
                .bind(0, id)
                .map { row -> mapRow(row) }
                .firstOrNull()
        }

    fun create(request: CreateEmailProviderRequest): EmailProviderDto {
        require(request.name.isNotBlank()) { "Provider name must not be blank" }
        require(request.host.isNotBlank()) { "Host must not be blank" }
        require(request.fromAddress.isNotBlank()) { "From address must not be blank" }
        require(request.port in 1..65535) { "Port must be between 1 and 65535" }

        val id = UUID.randomUUID().toString()
        val now = Instant.now().toString()

        jdbi.useHandle<Exception> { h ->
            if (request.isDefault) {
                h.createUpdate("UPDATE email_providers SET is_default = FALSE").execute()
            }
            h.createUpdate(
                "INSERT INTO email_providers (id, name, host, port, username, password, from_address, use_tls, is_default, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            )
                .bind(0, id)
                .bind(1, request.name)
                .bind(2, request.host)
                .bind(3, request.port)
                .bind(4, request.username)
                .bind(5, request.password)
                .bind(6, request.fromAddress)
                .bind(7, request.useTls)
                .bind(8, request.isDefault)
                .bind(9, now)
                .bind(10, now)
                .execute()
        }

        return EmailProviderDto(
            id = id, name = request.name, host = request.host, port = request.port,
            username = request.username, fromAddress = request.fromAddress,
            useTls = request.useTls, isDefault = request.isDefault,
            createdAt = now, updatedAt = now,
        )
    }

    fun update(id: String, request: UpdateEmailProviderRequest): EmailProviderDto? {
        val existing = get(id) ?: return null
        val now = Instant.now().toString()

        jdbi.useHandle<Exception> { h ->
            if (request.isDefault == true) {
                h.createUpdate("UPDATE email_providers SET is_default = FALSE").execute()
            }
            val sets = mutableListOf<String>()
            val bindings = mutableListOf<Any?>()
            request.name?.let { sets += "name = ?"; bindings += it }
            request.host?.let { sets += "host = ?"; bindings += it }
            request.port?.let { sets += "port = ?"; bindings += it }
            request.username?.let { sets += "username = ?"; bindings += it }
            request.password?.let { sets += "password = ?"; bindings += it }
            request.fromAddress?.let { sets += "from_address = ?"; bindings += it }
            request.useTls?.let { sets += "use_tls = ?"; bindings += it }
            request.isDefault?.let { sets += "is_default = ?"; bindings += it }
            sets += "updated_at = ?"; bindings += now
            bindings += id

            val stmt = h.createUpdate("UPDATE email_providers SET ${sets.joinToString(", ")} WHERE id = ?")
            bindings.forEachIndexed { idx, v -> stmt.bind(idx, v) }
            stmt.execute()
        }

        return get(id)
    }

    fun delete(id: String): Boolean {
        val rows = jdbi.withHandle<Int, Exception> { h ->
            h.createUpdate("DELETE FROM email_providers WHERE id = ?").bind(0, id).execute()
        }
        return rows > 0
    }

    /** Set the given provider as the default, demoting all others. */
    fun setDefault(id: String): Boolean {
        if (get(id) == null) return false
        jdbi.useHandle<Exception> { h ->
            h.createUpdate("UPDATE email_providers SET is_default = FALSE").execute()
            h.createUpdate("UPDATE email_providers SET is_default = TRUE, updated_at = ? WHERE id = ?")
                .bind(0, Instant.now().toString()).bind(1, id).execute()
        }
        return true
    }

    private fun mapRow(row: org.jdbi.v3.core.result.RowView) = EmailProviderDto(
        id = row.getColumn("id", String::class.java),
        name = row.getColumn("name", String::class.java),
        host = row.getColumn("host", String::class.java),
        port = (row.getColumn("port", java.lang.Integer::class.java) as? Int) ?: 587,
        username = row.getColumn("username", String::class.java),
        fromAddress = row.getColumn("from_address", String::class.java),
        useTls = row.getColumn("use_tls", java.lang.Boolean::class.java) == true,
        isDefault = row.getColumn("is_default", java.lang.Boolean::class.java) == true,
        createdAt = row.getColumn("created_at", String::class.java),
        updatedAt = row.getColumn("updated_at", String::class.java),
    )
}
