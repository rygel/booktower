package org.booktower.services

import org.booktower.models.BookExportDto
import org.booktower.models.BookmarkExportDto
import org.booktower.models.LibraryExportDto
import org.booktower.models.ProgressExportDto
import org.booktower.models.UserExportDto
import org.jdbi.v3.core.Jdbi
import org.slf4j.LoggerFactory
import java.util.UUID

private val logger = LoggerFactory.getLogger("booktower.ExportService")

class ExportService(
    private val jdbi: Jdbi,
) {
    fun exportUser(userId: UUID): UserExportDto {
        val uid = userId.toString()

        val user =
            jdbi.withHandle<Triple<String, String, String>?, Exception> { handle ->
                handle
                    .createQuery("SELECT username, email, created_at FROM users WHERE id = ?")
                    .bind(0, uid)
                    .map { r ->
                        Triple(
                            r.getColumn("username", String::class.java),
                            r.getColumn("email", String::class.java),
                            r.getColumn("created_at", String::class.java),
                        )
                    }.firstOrNull()
            } ?: throw IllegalArgumentException("User not found: $userId")

        val libraries =
            jdbi.withHandle<List<Triple<String, String, String>>, Exception> { handle ->
                handle
                    .createQuery("SELECT id, name, path, created_at FROM libraries WHERE user_id = ? ORDER BY created_at")
                    .bind(0, uid)
                    .map { r ->
                        Triple(
                            r.getColumn("id", String::class.java),
                            r.getColumn("name", String::class.java),
                            r.getColumn("path", String::class.java) + "|" + r.getColumn("created_at", String::class.java),
                        )
                    }.list()
            }

        val libraryDtos =
            libraries.map { (libId, libName, pathAndDate) ->
                val (path, createdAt) = pathAndDate.split("|", limit = 2)
                LibraryExportDto(
                    name = libName,
                    path = path,
                    books = exportBooks(userId, libId),
                    createdAt = createdAt,
                )
            }

        logger.info("Exported data for user $userId: ${libraryDtos.size} libraries")
        return UserExportDto(
            username = user.first,
            email = user.second,
            memberSince = user.third,
            libraries = libraryDtos,
        )
    }

    private fun exportBooks(
        userId: UUID,
        libraryId: String,
    ): List<BookExportDto> {
        val uid = userId.toString()

        data class BookRow(
            val id: String,
            val title: String,
            val author: String?,
            val description: String?,
            val series: String?,
            val seriesIndex: Double?,
            val addedAt: String,
        )

        val books =
            jdbi.withHandle<List<BookRow>, Exception> { handle ->
                handle
                    .createQuery(
                        "SELECT id, title, author, description, series, series_index, added_at FROM books WHERE library_id = ? ORDER BY added_at",
                    ).bind(0, libraryId)
                    .map { r ->
                        BookRow(
                            id = r.getColumn("id", String::class.java),
                            title = r.getColumn("title", String::class.java),
                            author = r.getColumn("author", String::class.java),
                            description = r.getColumn("description", String::class.java),
                            series = r.getColumn("series", String::class.java),
                            seriesIndex = r.getColumn("series_index", java.lang.Double::class.java)?.toDouble(),
                            addedAt = r.getColumn("added_at", String::class.java),
                        )
                    }.list()
            }

        return books.map { book ->
            val status =
                jdbi.withHandle<String?, Exception> { handle ->
                    handle
                        .createQuery("SELECT status FROM book_status WHERE user_id = ? AND book_id = ?")
                        .bind(0, uid)
                        .bind(1, book.id)
                        .mapTo(String::class.java)
                        .firstOrNull()
                }
            val rating =
                jdbi.withHandle<Int?, Exception> { handle ->
                    handle
                        .createQuery("SELECT rating FROM book_ratings WHERE user_id = ? AND book_id = ?")
                        .bind(0, uid)
                        .bind(1, book.id)
                        .mapTo(java.lang.Integer::class.java)
                        .firstOrNull()
                        ?.toInt()
                }
            val tags =
                jdbi.withHandle<List<String>, Exception> { handle ->
                    handle
                        .createQuery("SELECT tag FROM book_tags WHERE user_id = ? AND book_id = ? ORDER BY tag")
                        .bind(0, uid)
                        .bind(1, book.id)
                        .mapTo(String::class.java)
                        .list()
                }
            val progress =
                jdbi.withHandle<ProgressExportDto?, Exception> { handle ->
                    handle
                        .createQuery(
                            "SELECT current_page, total_pages, percentage, last_read_at FROM reading_progress WHERE user_id = ? AND book_id = ?",
                        ).bind(0, uid)
                        .bind(1, book.id)
                        .map { r ->
                            ProgressExportDto(
                                currentPage = r.getColumn("current_page", java.lang.Integer::class.java)?.toInt() ?: 0,
                                totalPages = r.getColumn("total_pages", java.lang.Integer::class.java)?.toInt(),
                                percentage = r.getColumn("percentage", java.lang.Double::class.java)?.toDouble(),
                                lastReadAt = r.getColumn("last_read_at", String::class.java),
                            )
                        }.firstOrNull()
                }
            val bookmarks =
                jdbi.withHandle<List<BookmarkExportDto>, Exception> { handle ->
                    handle
                        .createQuery(
                            "SELECT page, title, note, created_at FROM bookmarks WHERE user_id = ? AND book_id = ? ORDER BY page",
                        ).bind(0, uid)
                        .bind(1, book.id)
                        .map { r ->
                            BookmarkExportDto(
                                page = r.getColumn("page", java.lang.Integer::class.java)?.toInt() ?: 0,
                                title = r.getColumn("title", String::class.java),
                                note = r.getColumn("note", String::class.java),
                                createdAt = r.getColumn("created_at", String::class.java),
                            )
                        }.list()
                }

            BookExportDto(
                title = book.title,
                author = book.author,
                description = book.description,
                series = book.series,
                seriesIndex = book.seriesIndex,
                status = status,
                rating = rating,
                tags = tags,
                progress = progress,
                bookmarks = bookmarks,
                addedAt = book.addedAt,
            )
        }
    }
}
