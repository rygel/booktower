package org.booktower.services

import org.jdbi.v3.core.Jdbi
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

private val cfLog = LoggerFactory.getLogger("booktower.CustomFieldService")

data class CustomFieldDefinition(
    val id: String,
    val fieldName: String,
    val fieldType: String,
    val fieldOptions: String?,
    val sortOrder: Int,
)

data class CustomFieldValue(
    val id: String,
    val bookId: String,
    val fieldName: String,
    val fieldValue: String?,
)

data class CreateFieldDefinitionRequest(
    val fieldName: String,
    val fieldType: String = "text",
    val fieldOptions: String? = null,
)

/**
 * Manages user-defined custom metadata fields on books.
 *
 * Field types:
 * - `text` — free-form text (default)
 * - `number` — numeric value
 * - `date` — date string (YYYY-MM-DD)
 * - `select` — dropdown, options stored as comma-separated in fieldOptions
 * - `boolean` — true/false checkbox
 *
 * Each user defines their own field templates (definitions),
 * then sets values per book. Fields are user-scoped — different
 * users can have different custom fields.
 */
class CustomFieldService(
    private val jdbi: Jdbi,
) {
    // ── Field definitions ─────────────────────────────────────────────────

    fun getDefinitions(userId: UUID): List<CustomFieldDefinition> =
        jdbi.withHandle<List<CustomFieldDefinition>, Exception> { h ->
            h
                .createQuery(
                    "SELECT * FROM custom_field_definitions WHERE user_id = ? ORDER BY sort_order, field_name",
                ).bind(0, userId.toString())
                .map { row ->
                    CustomFieldDefinition(
                        id = row.getColumn("id", String::class.java) ?: "",
                        fieldName = row.getColumn("field_name", String::class.java) ?: "",
                        fieldType = row.getColumn("field_type", String::class.java) ?: "text",
                        fieldOptions = row.getColumn("field_options", String::class.java),
                        sortOrder = row.getColumn("sort_order", Int::class.javaObjectType) ?: 0,
                    )
                }.list()
        }

    fun createDefinition(
        userId: UUID,
        request: CreateFieldDefinitionRequest,
    ): CustomFieldDefinition {
        val id = UUID.randomUUID().toString()
        val now = Instant.now().toString()
        val name = request.fieldName.trim().take(100)
        val type = request.fieldType.takeIf { it in VALID_TYPES } ?: "text"

        val maxOrder =
            jdbi.withHandle<Int, Exception> { h ->
                h
                    .createQuery("SELECT COALESCE(MAX(sort_order), 0) FROM custom_field_definitions WHERE user_id = ?")
                    .bind(0, userId.toString())
                    .mapTo(Int::class.javaObjectType)
                    .one()
            }

        jdbi.useHandle<Exception> { h ->
            h
                .createUpdate(
                    """
                INSERT INTO custom_field_definitions (id, user_id, field_name, field_type, field_options, sort_order, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                ).bind(0, id)
                .bind(1, userId.toString())
                .bind(2, name)
                .bind(3, type)
                .bind(4, request.fieldOptions)
                .bind(5, maxOrder + 1)
                .bind(6, now)
                .execute()
        }
        cfLog.info("Custom field defined: $name ($type) for user $userId")
        return CustomFieldDefinition(id, name, type, request.fieldOptions, maxOrder + 1)
    }

    fun deleteDefinition(
        userId: UUID,
        definitionId: String,
    ): Boolean {
        // Also delete all values for this field
        val fieldName =
            jdbi.withHandle<String?, Exception> { h ->
                h
                    .createQuery("SELECT field_name FROM custom_field_definitions WHERE id = ? AND user_id = ?")
                    .bind(0, definitionId)
                    .bind(1, userId.toString())
                    .mapTo(String::class.java)
                    .firstOrNull()
            } ?: return false

        jdbi.useHandle<Exception> { h ->
            h
                .createUpdate("DELETE FROM book_custom_fields WHERE user_id = ? AND field_name = ?")
                .bind(0, userId.toString())
                .bind(1, fieldName)
                .execute()
            h
                .createUpdate("DELETE FROM custom_field_definitions WHERE id = ? AND user_id = ?")
                .bind(0, definitionId)
                .bind(1, userId.toString())
                .execute()
        }
        cfLog.info("Custom field deleted: $fieldName for user $userId")
        return true
    }

    // ── Field values per book ─────────────────────────────────────────────

    fun getValues(
        userId: UUID,
        bookId: UUID,
    ): List<CustomFieldValue> =
        jdbi.withHandle<List<CustomFieldValue>, Exception> { h ->
            h
                .createQuery(
                    "SELECT * FROM book_custom_fields WHERE user_id = ? AND book_id = ? ORDER BY field_name",
                ).bind(0, userId.toString())
                .bind(1, bookId.toString())
                .map { row ->
                    CustomFieldValue(
                        id = row.getColumn("id", String::class.java) ?: "",
                        bookId = row.getColumn("book_id", String::class.java) ?: "",
                        fieldName = row.getColumn("field_name", String::class.java) ?: "",
                        fieldValue = row.getColumn("field_value", String::class.java),
                    )
                }.list()
        }

    fun setValue(
        userId: UUID,
        bookId: UUID,
        fieldName: String,
        fieldValue: String?,
    ) {
        val now = Instant.now().toString()
        val uid = userId.toString()
        val bid = bookId.toString()
        val name = fieldName.trim().take(100)

        val exists =
            jdbi.withHandle<Boolean, Exception> { h ->
                h
                    .createQuery("SELECT COUNT(*) FROM book_custom_fields WHERE user_id = ? AND book_id = ? AND field_name = ?")
                    .bind(0, uid)
                    .bind(1, bid)
                    .bind(2, name)
                    .mapTo(Int::class.javaObjectType)
                    .one() > 0
            }

        if (exists) {
            jdbi.useHandle<Exception> { h ->
                h
                    .createUpdate("UPDATE book_custom_fields SET field_value = ?, updated_at = ? WHERE user_id = ? AND book_id = ? AND field_name = ?")
                    .bind(0, fieldValue)
                    .bind(1, now)
                    .bind(2, uid)
                    .bind(3, bid)
                    .bind(4, name)
                    .execute()
            }
        } else {
            jdbi.useHandle<Exception> { h ->
                h
                    .createUpdate(
                        """
                    INSERT INTO book_custom_fields (id, user_id, book_id, field_name, field_value, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """,
                    ).bind(0, UUID.randomUUID().toString())
                    .bind(1, uid)
                    .bind(2, bid)
                    .bind(3, name)
                    .bind(4, fieldValue)
                    .bind(5, now)
                    .bind(6, now)
                    .execute()
            }
        }
    }

    fun deleteValue(
        userId: UUID,
        bookId: UUID,
        fieldName: String,
    ): Boolean {
        val deleted =
            jdbi.withHandle<Int, Exception> { h ->
                h
                    .createUpdate("DELETE FROM book_custom_fields WHERE user_id = ? AND book_id = ? AND field_name = ?")
                    .bind(0, userId.toString())
                    .bind(1, bookId.toString())
                    .bind(2, fieldName.trim())
                    .execute()
            }
        return deleted > 0
    }

    companion object {
        private val VALID_TYPES = setOf("text", "number", "date", "select", "boolean")
    }
}
