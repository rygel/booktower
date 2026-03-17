package org.booktower.services

import org.jdbi.v3.core.Jdbi
import java.time.Instant

data class AudiobookMetaDto(
    val bookId: String,
    val narrator: String?,
    val abridged: Boolean,
    val audioCover: String?,
    val durationSec: Int?,
    val updatedAt: String,
)

data class UpdateAudiobookMetaRequest(
    val narrator: String? = null,
    val abridged: Boolean? = null,
    val audioCover: String? = null,
    val durationSec: Int? = null,
)

class AudiobookMetaService(private val jdbi: Jdbi) {

    fun get(bookId: String): AudiobookMetaDto? =
        jdbi.withHandle<AudiobookMetaDto?, Exception> { h ->
            h.createQuery(
                "SELECT book_id, narrator, abridged, audio_cover, duration_sec, updated_at FROM book_audiobook_meta WHERE book_id = ?",
            ).bind(0, bookId).map { row -> mapRow(row) }.firstOrNull()
        }

    fun upsert(bookId: String, request: UpdateAudiobookMetaRequest): AudiobookMetaDto {
        val now = Instant.now().toString()
        val existing = get(bookId)
        if (existing == null) {
            jdbi.useHandle<Exception> { h ->
                h.createUpdate(
                    "INSERT INTO book_audiobook_meta (book_id, narrator, abridged, audio_cover, duration_sec, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
                ).bind(0, bookId)
                    .bind(1, request.narrator)
                    .bind(2, request.abridged ?: false)
                    .bind(3, request.audioCover)
                    .bind(4, request.durationSec)
                    .bind(5, now)
                    .execute()
            }
        } else {
            jdbi.useHandle<Exception> { h ->
                h.createUpdate(
                    "UPDATE book_audiobook_meta SET narrator = ?, abridged = ?, audio_cover = ?, duration_sec = ?, updated_at = ? WHERE book_id = ?",
                ).bind(0, request.narrator ?: existing.narrator)
                    .bind(1, request.abridged ?: existing.abridged)
                    .bind(2, request.audioCover ?: existing.audioCover)
                    .bind(3, request.durationSec ?: existing.durationSec)
                    .bind(4, now)
                    .bind(5, bookId)
                    .execute()
            }
        }
        return get(bookId)!!
    }

    fun delete(bookId: String): Boolean {
        val rows = jdbi.withHandle<Int, Exception> { h ->
            h.createUpdate("DELETE FROM book_audiobook_meta WHERE book_id = ?")
                .bind(0, bookId).execute()
        }
        return rows > 0
    }

    private fun mapRow(row: org.jdbi.v3.core.result.RowView) = AudiobookMetaDto(
        bookId = row.getColumn("book_id", String::class.java),
        narrator = row.getColumn("narrator", String::class.java),
        abridged = row.getColumn("abridged", java.lang.Boolean::class.java) == true,
        audioCover = row.getColumn("audio_cover", String::class.java),
        durationSec = (row.getColumn("duration_sec", java.lang.Integer::class.java) as? Int),
        updatedAt = row.getColumn("updated_at", String::class.java),
    )
}
