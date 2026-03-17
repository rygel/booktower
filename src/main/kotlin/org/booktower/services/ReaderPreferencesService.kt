package org.booktower.services

import com.fasterxml.jackson.databind.node.ObjectNode
import org.booktower.config.Json
import java.util.UUID

/**
 * Stores per-format reader preferences using the existing UserSettingsService.
 * Keys are `reader_prefs_{format}` (e.g. `reader_prefs_epub`, `reader_prefs_pdf`).
 *
 * Preference values are free-form JSON objects so the frontend can evolve them
 * without requiring new migrations.
 */
class ReaderPreferencesService(private val userSettingsService: UserSettingsService) {

    companion object {
        private val SUPPORTED_FORMATS = setOf("epub", "pdf", "cbz", "cbr", "comic")

        fun keyFor(format: String) = "reader_prefs_${format.lowercase()}"
    }

    fun get(userId: UUID, format: String): Map<String, Any?> {
        val key = keyFor(format)
        val raw = userSettingsService.get(userId, key) ?: return emptyMap()
        return try {
            @Suppress("UNCHECKED_CAST")
            Json.mapper.readValue(raw, Map::class.java) as Map<String, Any?>
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun set(userId: UUID, format: String, prefs: Map<String, Any?>): Map<String, Any?> {
        val key = keyFor(format)
        val json = Json.mapper.writeValueAsString(prefs)
        userSettingsService.set(userId, key, json)
        return prefs
    }

    fun delete(userId: UUID, format: String) {
        userSettingsService.delete(userId, keyFor(format))
    }

    /** Merges new preference values into existing ones (partial update). */
    fun merge(userId: UUID, format: String, updates: Map<String, Any?>): Map<String, Any?> {
        val current = get(userId, format).toMutableMap()
        current.putAll(updates)
        return set(userId, format, current)
    }
}
