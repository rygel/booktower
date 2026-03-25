package org.runary.services

import org.runary.config.Json
import java.util.UUID

/**
 * Stores per-format reader preferences using the existing UserSettingsService.
 * Keys are `reader_prefs_{format}` (e.g. `reader_prefs_epub`, `reader_prefs_pdf`).
 *
 * Preference values are free-form JSON objects so the frontend can evolve them
 * without requiring new migrations.
 */
class ReaderPreferencesService(
    private val userSettingsService: UserSettingsService,
) {
    companion object {
        private val SUPPORTED_FORMATS = setOf("epub", "pdf", "cbz", "cbr", "comic")
        private val DEVICE_RE = Regex("^[0-9a-f]{1,12}$")

        fun keyFor(
            format: String,
            device: String? = null,
        ): String {
            val base = "reader_prefs_${format.lowercase()}"
            val suffix = device?.takeIf { DEVICE_RE.matches(it) }
            return if (suffix != null) "${base}_$suffix" else base
        }
    }

    fun get(
        userId: UUID,
        format: String,
        device: String? = null,
    ): Map<String, Any?> {
        val raw = userSettingsService.get(userId, keyFor(format, device)) ?: return emptyMap()
        return try {
            @Suppress("UNCHECKED_CAST")
            Json.mapper.readValue(raw, Map::class.java) as Map<String, Any?>
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun set(
        userId: UUID,
        format: String,
        prefs: Map<String, Any?>,
        device: String? = null,
    ): Map<String, Any?> {
        val json = Json.mapper.writeValueAsString(prefs)
        userSettingsService.set(userId, keyFor(format, device), json)
        return prefs
    }

    fun delete(
        userId: UUID,
        format: String,
        device: String? = null,
    ) {
        userSettingsService.delete(userId, keyFor(format, device))
    }

    /** Merges new preference values into existing ones (partial update). */
    fun merge(
        userId: UUID,
        format: String,
        updates: Map<String, Any?>,
        device: String? = null,
    ): Map<String, Any?> {
        val current = get(userId, format, device).toMutableMap()
        current.putAll(updates)
        return set(userId, format, current, device)
    }
}
