package org.runary.services

import org.jdbi.v3.core.Jdbi
import java.time.Instant
import java.util.UUID

data class BookCondition(
    val bookId: String,
    val condition: String?,
    val purchasePrice: String?,
    val purchaseDate: String?,
    val purchaseSource: String?,
    val shelfLocation: String?,
    val notes: String?,
)

data class UpdateBookConditionRequest(
    val condition: String? = null,
    val purchasePrice: String? = null,
    val purchaseDate: String? = null,
    val purchaseSource: String? = null,
    val shelfLocation: String? = null,
    val notes: String? = null,
)

/**
 * Tracks physical book condition and collection details.
 * Uses the custom_fields infrastructure (book_custom_fields table)
 * with reserved field names prefixed with `_condition.`.
 */
class BookConditionService(
    private val customFieldService: CustomFieldService,
) {
    companion object {
        private const val PREFIX = "_condition."
    }

    fun get(
        userId: UUID,
        bookId: UUID,
    ): BookCondition {
        val values = customFieldService.getValues(userId, bookId)
        val map = values.filter { it.fieldName.startsWith(PREFIX) }.associate { it.fieldName.removePrefix(PREFIX) to it.fieldValue }
        return BookCondition(
            bookId = bookId.toString(),
            condition = map["condition"],
            purchasePrice = map["purchasePrice"],
            purchaseDate = map["purchaseDate"],
            purchaseSource = map["purchaseSource"],
            shelfLocation = map["shelfLocation"],
            notes = map["notes"],
        )
    }

    fun update(
        userId: UUID,
        bookId: UUID,
        request: UpdateBookConditionRequest,
    ) {
        val fields =
            mapOf(
                "condition" to request.condition,
                "purchasePrice" to request.purchasePrice,
                "purchaseDate" to request.purchaseDate,
                "purchaseSource" to request.purchaseSource,
                "shelfLocation" to request.shelfLocation,
                "notes" to request.notes,
            )
        for ((key, value) in fields) {
            if (value != null) {
                customFieldService.setValue(userId, bookId, "$PREFIX$key", value)
            }
        }
    }
}
