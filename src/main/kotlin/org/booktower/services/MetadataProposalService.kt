package org.booktower.services

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.booktower.models.FetchedMetadata
import org.jdbi.v3.core.Jdbi
import java.time.Instant
import java.util.UUID

private val jsonMapper = jacksonObjectMapper()

data class MetadataProposalDto(
    val id: String,
    val bookId: String,
    val source: String,
    val metadata: FetchedMetadata,
    val status: String,
    val proposedAt: String,
    val reviewedAt: String?,
)

class MetadataProposalService(private val jdbi: Jdbi) {

    /** Store a fetched-metadata result as a pending proposal for a book. */
    fun propose(userId: UUID, bookId: UUID, metadata: FetchedMetadata): MetadataProposalDto {
        val id = UUID.randomUUID()
        val now = Instant.now().toString()
        val dataJson = jsonMapper.writeValueAsString(metadata)
        jdbi.useHandle<Exception> { h ->
            h.createUpdate(
                """INSERT INTO metadata_proposals (id, book_id, user_id, source, data_json, status, proposed_at)
                   VALUES (?, ?, ?, ?, ?, 'PENDING', ?)""",
            )
                .bind(0, id.toString())
                .bind(1, bookId.toString())
                .bind(2, userId.toString())
                .bind(3, metadata.source ?: "unknown")
                .bind(4, dataJson)
                .bind(5, now)
                .execute()
        }
        return MetadataProposalDto(
            id = id.toString(),
            bookId = bookId.toString(),
            source = metadata.source ?: "unknown",
            metadata = metadata,
            status = "PENDING",
            proposedAt = now,
            reviewedAt = null,
        )
    }

    /** List pending proposals for a book owned by userId. */
    fun listProposals(userId: UUID, bookId: UUID): List<MetadataProposalDto> =
        jdbi.withHandle<List<MetadataProposalDto>, Exception> { h ->
            h.createQuery(
                """SELECT id, book_id, source, data_json, status, proposed_at, reviewed_at
                   FROM metadata_proposals
                   WHERE book_id = ? AND user_id = ? AND status = 'PENDING'
                   ORDER BY proposed_at DESC""",
            )
                .bind(0, bookId.toString())
                .bind(1, userId.toString())
                .map { row -> rowToDto(row) }
                .list()
        }

    /** Mark a proposal as APPLIED and return its metadata (for the caller to apply). */
    fun applyProposal(userId: UUID, bookId: UUID, proposalId: UUID): FetchedMetadata? {
        val now = Instant.now().toString()
        val updated = jdbi.withHandle<Int, Exception> { h ->
            h.createUpdate(
                """UPDATE metadata_proposals SET status = 'APPLIED', reviewed_at = ?
                   WHERE id = ? AND book_id = ? AND user_id = ? AND status = 'PENDING'""",
            )
                .bind(0, now)
                .bind(1, proposalId.toString())
                .bind(2, bookId.toString())
                .bind(3, userId.toString())
                .execute()
        }
        if (updated == 0) return null
        return jdbi.withHandle<FetchedMetadata?, Exception> { h ->
            h.createQuery("SELECT data_json FROM metadata_proposals WHERE id = ?")
                .bind(0, proposalId.toString())
                .map { row -> jsonMapper.readValue(row.getColumn("data_json", String::class.java), FetchedMetadata::class.java) }
                .firstOrNull()
        }
    }

    /** Mark a proposal as DISMISSED. Returns true if found and updated. */
    fun dismissProposal(userId: UUID, bookId: UUID, proposalId: UUID): Boolean {
        val now = Instant.now().toString()
        val updated = jdbi.withHandle<Int, Exception> { h ->
            h.createUpdate(
                """UPDATE metadata_proposals SET status = 'DISMISSED', reviewed_at = ?
                   WHERE id = ? AND book_id = ? AND user_id = ? AND status = 'PENDING'""",
            )
                .bind(0, now)
                .bind(1, proposalId.toString())
                .bind(2, bookId.toString())
                .bind(3, userId.toString())
                .execute()
        }
        return updated > 0
    }

    private fun rowToDto(row: org.jdbi.v3.core.result.RowView): MetadataProposalDto {
        val dataJson = row.getColumn("data_json", String::class.java)
        val meta = jsonMapper.readValue(dataJson, FetchedMetadata::class.java)
        return MetadataProposalDto(
            id = row.getColumn("id", String::class.java),
            bookId = row.getColumn("book_id", String::class.java),
            source = row.getColumn("source", String::class.java),
            metadata = meta,
            status = row.getColumn("status", String::class.java),
            proposedAt = row.getColumn("proposed_at", String::class.java),
            reviewedAt = row.getColumn("reviewed_at", String::class.java),
        )
    }
}
