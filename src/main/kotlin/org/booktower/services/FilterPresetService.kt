package org.booktower.services

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.jdbi.v3.core.Jdbi
import java.time.Instant
import java.util.UUID

private val mapper = ObjectMapper()

data class FilterPresetDto(
    val id: String,
    val userId: String,
    val name: String,
    val filters: String,
    val createdAt: String,
    val updatedAt: String,
)

data class SaveFilterPresetRequest(
    val name: String,
    val filters: JsonNode,
) {
    val filtersAsString: String get() = mapper.writeValueAsString(filters)
}

class FilterPresetService(
    private val jdbi: Jdbi,
) {
    fun list(userId: UUID): List<FilterPresetDto> =
        jdbi.withHandle<List<FilterPresetDto>, Exception> { h ->
            h
                .createQuery(
                    "SELECT id, user_id, name, filters, created_at, updated_at FROM filter_presets WHERE user_id = ? ORDER BY name",
                ).bind(0, userId.toString())
                .map { row -> mapRow(row) }
                .list()
        }

    fun get(
        userId: UUID,
        presetId: String,
    ): FilterPresetDto? =
        jdbi.withHandle<FilterPresetDto?, Exception> { h ->
            h
                .createQuery(
                    "SELECT id, user_id, name, filters, created_at, updated_at FROM filter_presets WHERE id = ? AND user_id = ?",
                ).bind(0, presetId)
                .bind(1, userId.toString())
                .map { row -> mapRow(row) }
                .firstOrNull()
        }

    fun create(
        userId: UUID,
        request: SaveFilterPresetRequest,
    ): FilterPresetDto {
        require(request.name.isNotBlank()) { "Preset name must not be blank" }
        require(!request.filters.isNull) { "Filters must not be null" }
        val id = UUID.randomUUID().toString()
        val now = Instant.now().toString()
        jdbi.useHandle<Exception> { h ->
            h
                .createUpdate(
                    "INSERT INTO filter_presets (id, user_id, name, filters, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
                ).bind(0, id)
                .bind(1, userId.toString())
                .bind(2, request.name)
                .bind(3, request.filtersAsString)
                .bind(4, now)
                .bind(5, now)
                .execute()
        }
        return FilterPresetDto(
            id = id,
            userId = userId.toString(),
            name = request.name,
            filters = request.filtersAsString,
            createdAt = now,
            updatedAt = now,
        )
    }

    fun update(
        userId: UUID,
        presetId: String,
        request: SaveFilterPresetRequest,
    ): FilterPresetDto? {
        get(userId, presetId) ?: return null
        require(request.name.isNotBlank()) { "Preset name must not be blank" }
        val now = Instant.now().toString()
        jdbi.useHandle<Exception> { h ->
            h
                .createUpdate(
                    "UPDATE filter_presets SET name = ?, filters = ?, updated_at = ? WHERE id = ? AND user_id = ?",
                ).bind(0, request.name)
                .bind(1, request.filtersAsString)
                .bind(2, now)
                .bind(3, presetId)
                .bind(4, userId.toString())
                .execute()
        }
        return get(userId, presetId)
    }

    fun delete(
        userId: UUID,
        presetId: String,
    ): Boolean {
        val rows =
            jdbi.withHandle<Int, Exception> { h ->
                h
                    .createUpdate("DELETE FROM filter_presets WHERE id = ? AND user_id = ?")
                    .bind(0, presetId)
                    .bind(1, userId.toString())
                    .execute()
            }
        return rows > 0
    }

    private fun mapRow(row: org.jdbi.v3.core.result.RowView) =
        FilterPresetDto(
            id = row.getColumn("id", String::class.java),
            userId = row.getColumn("user_id", String::class.java),
            name = row.getColumn("name", String::class.java),
            filters = row.getColumn("filters", String::class.java),
            createdAt = row.getColumn("created_at", String::class.java),
            updatedAt = row.getColumn("updated_at", String::class.java),
        )
}
