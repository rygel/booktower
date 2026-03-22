package org.booktower.services

import org.jdbi.v3.core.Jdbi
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

private val posLog = LoggerFactory.getLogger("booktower.PositionSyncService")

data class SyncedPosition(
    val bookId: String,
    val currentPage: Int,
    val percentage: Double,
    val positionData: String?,
    val deviceId: String?,
    val lastReadAt: String,
)

data class UpdatePositionRequest(
    val currentPage: Int,
    val positionData: String? = null,
    val deviceId: String? = null,
)

/**
 * Cross-device reading position sync.
 * Stores detailed position data (EPUB CFI, PDF scroll, audio timestamp)
 * alongside the basic page/percentage so any device can resume reading.
 *
 * Push: device sends its current position after each page turn.
 * Pull: device fetches the latest position when opening a book.
 *
 * Conflict resolution: last-write-wins (most recent lastReadAt).
 */
class PositionSyncService(
    private val jdbi: Jdbi,
) {
    /**
     * Push reading position from a device.
     * Updates the reading_progress row with position data and device ID.
     */
    fun push(
        userId: UUID,
        bookId: UUID,
        request: UpdatePositionRequest,
    ) {
        val now = Instant.now().toString()
        val uid = userId.toString()
        val bid = bookId.toString()

        val existing =
            jdbi.withHandle<Boolean, Exception> { h ->
                h
                    .createQuery("SELECT COUNT(*) FROM reading_progress WHERE user_id = ? AND book_id = ?")
                    .bind(0, uid)
                    .bind(1, bid)
                    .mapTo(Int::class.javaObjectType)
                    .one() > 0
            }

        if (existing) {
            jdbi.useHandle<Exception> { h ->
                h
                    .createUpdate(
                        """
                    UPDATE reading_progress
                    SET current_page = ?, percentage = ?, position_data = ?,
                        device_id = ?, last_read_at = ?
                    WHERE user_id = ? AND book_id = ?
                    """,
                    ).bind(0, request.currentPage)
                    .bind(1, computePercentage(uid, bid, request.currentPage))
                    .bind(2, request.positionData)
                    .bind(3, request.deviceId?.take(50))
                    .bind(4, now)
                    .bind(5, uid)
                    .bind(6, bid)
                    .execute()
            }
        } else {
            jdbi.useHandle<Exception> { h ->
                h
                    .createUpdate(
                        """
                    INSERT INTO reading_progress
                        (id, user_id, book_id, current_page, total_pages, percentage,
                         position_data, device_id, last_read_at, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    ).bind(0, UUID.randomUUID().toString())
                    .bind(1, uid)
                    .bind(2, bid)
                    .bind(3, request.currentPage)
                    .bind(4, getPageCount(uid, bid))
                    .bind(5, computePercentage(uid, bid, request.currentPage))
                    .bind(6, request.positionData)
                    .bind(7, request.deviceId?.take(50))
                    .bind(8, now)
                    .bind(9, now)
                    .execute()
            }
        }
        posLog.debug("Position synced: user=$userId book=$bookId page=${request.currentPage} device=${request.deviceId}")
    }

    /**
     * Pull the latest reading position for a book.
     * Returns null if no position has been saved.
     */
    fun pull(
        userId: UUID,
        bookId: UUID,
    ): SyncedPosition? =
        jdbi.withHandle<SyncedPosition?, Exception> { h ->
            h
                .createQuery(
                    """
                SELECT current_page, percentage, position_data, device_id, last_read_at
                FROM reading_progress
                WHERE user_id = ? AND book_id = ?
                """,
                ).bind(0, userId.toString())
                .bind(1, bookId.toString())
                .map { row ->
                    SyncedPosition(
                        bookId = bookId.toString(),
                        currentPage = row.getColumn("current_page", Int::class.javaObjectType) ?: 0,
                        percentage = row.getColumn("percentage", java.lang.Double::class.java)?.toDouble() ?: 0.0,
                        positionData = row.getColumn("position_data", String::class.java),
                        deviceId = row.getColumn("device_id", String::class.java),
                        lastReadAt = row.getColumn("last_read_at", String::class.java) ?: "",
                    )
                }.firstOrNull()
        }

    private fun getPageCount(
        uid: String,
        bid: String,
    ): Int? =
        jdbi.withHandle<Int?, Exception> { h ->
            h
                .createQuery("SELECT page_count FROM books WHERE id = ?")
                .bind(0, bid)
                .mapTo(Int::class.javaObjectType)
                .firstOrNull()
        }

    private fun computePercentage(
        uid: String,
        bid: String,
        currentPage: Int,
    ): Double {
        val total = getPageCount(uid, bid) ?: return 0.0
        return if (total > 0) (currentPage.toDouble() / total * 100).coerceIn(0.0, 100.0) else 0.0
    }
}
