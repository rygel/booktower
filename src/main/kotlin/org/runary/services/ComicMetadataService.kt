package org.runary.services

import org.runary.models.ComicMetadataRequest
import org.jdbi.v3.core.Jdbi
import java.time.Instant

data class ComicMetadata(
    val issueNumber: String?,
    val volumeNumber: String?,
    val comicSeries: String?,
    val coverDate: String?,
    val storyArc: String?,
    val characters: List<String>,
    val teams: List<String>,
    val locations: List<String>,
)

class ComicMetadataService(
    private val jdbi: Jdbi,
) {
    fun get(bookId: String): ComicMetadata =
        jdbi.withHandle<ComicMetadata, Exception> { h ->
            val row =
                h
                    .createQuery(
                        "SELECT issue_number, volume_number, comic_series, cover_date, story_arc FROM books WHERE id = ?",
                    ).bind(0, bookId)
                    .mapToMap()
                    .firstOrNull() ?: emptyMap()

            val characters =
                h
                    .createQuery("SELECT name FROM book_characters WHERE book_id = ? ORDER BY name")
                    .bind(0, bookId)
                    .mapTo(String::class.java)
                    .list()
            val teams =
                h
                    .createQuery("SELECT name FROM book_teams WHERE book_id = ? ORDER BY name")
                    .bind(0, bookId)
                    .mapTo(String::class.java)
                    .list()
            val locations =
                h
                    .createQuery("SELECT name FROM book_locations WHERE book_id = ? ORDER BY name")
                    .bind(0, bookId)
                    .mapTo(String::class.java)
                    .list()

            ComicMetadata(
                issueNumber = row["issue_number"] as? String,
                volumeNumber = row["volume_number"] as? String,
                comicSeries = row["comic_series"] as? String,
                coverDate = row["cover_date"] as? String,
                storyArc = row["story_arc"] as? String,
                characters = characters,
                teams = teams,
                locations = locations,
            )
        }

    fun update(
        bookId: String,
        request: ComicMetadataRequest,
    ): ComicMetadata {
        val now = Instant.now().toString()
        jdbi.useHandle<Exception> { h ->
            h
                .createUpdate(
                    "UPDATE books SET issue_number = ?, volume_number = ?, comic_series = ?, cover_date = ?, story_arc = ?, updated_at = ? WHERE id = ?",
                ).bind(0, request.issueNumber)
                .bind(1, request.volumeNumber)
                .bind(2, request.comicSeries)
                .bind(3, request.coverDate)
                .bind(4, request.storyArc)
                .bind(5, now)
                .bind(6, bookId)
                .execute()

            if (request.characters != null) {
                h.createUpdate("DELETE FROM book_characters WHERE book_id = ?").bind(0, bookId).execute()
                for (name in request.characters) {
                    if (name.isBlank()) continue
                    h
                        .createUpdate("INSERT INTO book_characters (book_id, name) VALUES (?, ?)")
                        .bind(0, bookId)
                        .bind(1, name)
                        .execute()
                }
            }
            if (request.teams != null) {
                h.createUpdate("DELETE FROM book_teams WHERE book_id = ?").bind(0, bookId).execute()
                for (name in request.teams) {
                    if (name.isBlank()) continue
                    h
                        .createUpdate("INSERT INTO book_teams (book_id, name) VALUES (?, ?)")
                        .bind(0, bookId)
                        .bind(1, name)
                        .execute()
                }
            }
            if (request.locations != null) {
                h.createUpdate("DELETE FROM book_locations WHERE book_id = ?").bind(0, bookId).execute()
                for (name in request.locations) {
                    if (name.isBlank()) continue
                    h
                        .createUpdate("INSERT INTO book_locations (book_id, name) VALUES (?, ?)")
                        .bind(0, bookId)
                        .bind(1, name)
                        .execute()
                }
            }
        }
        return get(bookId)
    }
}
