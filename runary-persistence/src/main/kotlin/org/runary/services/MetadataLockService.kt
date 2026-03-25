package org.runary.services

import org.jdbi.v3.core.Jdbi
import java.time.Instant
import java.util.UUID

/**
 * Manages per-field metadata locks for books.
 * A locked field will not be overwritten by automatic metadata fetching.
 */
class MetadataLockService(
    private val jdbi: Jdbi,
) {
    /** Returns the set of locked field names for a book. */
    fun getLockedFields(bookId: UUID): Set<String> =
        jdbi.withHandle<Set<String>, Exception> { h ->
            h
                .createQuery("SELECT field_name FROM book_metadata_locks WHERE book_id = ?")
                .bind(0, bookId.toString())
                .mapTo(String::class.java)
                .set()
        }

    /** Locks a list of fields for a book. Idempotent. */
    fun lockFields(
        bookId: UUID,
        fields: List<String>,
    ) {
        jdbi.useHandle<Exception> { h ->
            fields.forEach { field ->
                val exists =
                    h
                        .createQuery("SELECT COUNT(*) FROM book_metadata_locks WHERE book_id = ? AND field_name = ?")
                        .bind(0, bookId.toString())
                        .bind(1, field)
                        .mapTo(Int::class.javaObjectType)
                        .firstOrNull()!! > 0
                if (!exists) {
                    h
                        .createUpdate("INSERT INTO book_metadata_locks (book_id, field_name, locked_at) VALUES (?, ?, ?)")
                        .bind(0, bookId.toString())
                        .bind(1, field)
                        .bind(2, Instant.now().toString())
                        .execute()
                }
            }
        }
    }

    /** Unlocks a list of fields for a book. */
    fun unlockFields(
        bookId: UUID,
        fields: List<String>,
    ) {
        jdbi.useHandle<Exception> { h ->
            fields.forEach { field ->
                h
                    .createUpdate("DELETE FROM book_metadata_locks WHERE book_id = ? AND field_name = ?")
                    .bind(0, bookId.toString())
                    .bind(1, field)
                    .execute()
            }
        }
    }

    /** Replaces the full lock set for a book. */
    fun setLockedFields(
        bookId: UUID,
        fields: List<String>,
    ) {
        jdbi.useHandle<Exception> { h ->
            h
                .createUpdate("DELETE FROM book_metadata_locks WHERE book_id = ?")
                .bind(0, bookId.toString())
                .execute()
            fields.forEach { field ->
                h
                    .createUpdate("INSERT INTO book_metadata_locks (book_id, field_name, locked_at) VALUES (?, ?, ?)")
                    .bind(0, bookId.toString())
                    .bind(1, field)
                    .bind(2, Instant.now().toString())
                    .execute()
            }
        }
    }

    /** Returns true if a given field is locked for the book. */
    fun isLocked(
        bookId: UUID,
        field: String,
    ): Boolean =
        jdbi.withHandle<Boolean, Exception> { h ->
            h
                .createQuery("SELECT COUNT(*) FROM book_metadata_locks WHERE book_id = ? AND field_name = ?")
                .bind(0, bookId.toString())
                .bind(1, field)
                .mapTo(Int::class.javaObjectType)
                .firstOrNull()!! > 0
        }
}
