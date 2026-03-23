package org.runary.services

import org.jdbi.v3.core.Jdbi
import org.slf4j.LoggerFactory
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

private val fontLogger = LoggerFactory.getLogger("runary.FontService")

private val ALLOWED_FONT_EXTENSIONS = setOf("ttf", "otf", "woff", "woff2")

data class UserFont(
    val id: String,
    val userId: UUID,
    val filename: String,
    val originalName: String,
    val fileSize: Long,
    val createdAt: Instant,
    val url: String,
)

class FontService(
    private val jdbi: Jdbi,
    private val fontsPath: String,
) {
    fun listFonts(userId: UUID): List<UserFont> =
        jdbi.withHandle<List<UserFont>, Exception> { h ->
            h
                .createQuery("SELECT * FROM user_fonts WHERE user_id = ? ORDER BY original_name")
                .bind(0, userId.toString())
                .map { row -> mapFont(userId, row) }
                .list()
        }

    fun uploadFont(
        userId: UUID,
        originalName: String,
        bytes: ByteArray,
    ): Result<UserFont> {
        val ext = originalName.substringAfterLast('.', "").lowercase()
        if (ext !in ALLOWED_FONT_EXTENSIONS) {
            return Result.failure(IllegalArgumentException("Unsupported font type: .$ext"))
        }
        val id = UUID.randomUUID().toString()
        val safeBase = originalName.substringBeforeLast('.').replace(Regex("[^a-zA-Z0-9_.-]"), "_").take(60)
        val filename = "${id}_$safeBase.$ext"
        val userDir =
            File(fontsPath, userId.toString()).also {
                if (!it.mkdirs() && !it.exists()) fontLogger.warn("Could not create directory: ${it.absolutePath}")
            }
        val dest = File(userDir, filename)
        dest.writeBytes(bytes)
        val now = Instant.now()
        jdbi.useHandle<Exception> { h ->
            h
                .createUpdate(
                    "INSERT INTO user_fonts (id, user_id, filename, original_name, file_size, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                ).bind(0, id)
                .bind(1, userId.toString())
                .bind(2, filename)
                .bind(3, originalName)
                .bind(4, bytes.size.toLong())
                .bind(5, now.toString())
                .execute()
        }
        fontLogger.info("Font uploaded: $originalName for user $userId")
        return Result.success(
            UserFont(
                id = id,
                userId = userId,
                filename = filename,
                originalName = originalName,
                fileSize = bytes.size.toLong(),
                createdAt = now,
                url = "/fonts/$userId/$filename",
            ),
        )
    }

    fun deleteFont(
        userId: UUID,
        fontId: String,
    ): Boolean {
        val font =
            jdbi.withHandle<UserFont?, Exception> { h ->
                h
                    .createQuery("SELECT * FROM user_fonts WHERE id = ? AND user_id = ?")
                    .bind(0, fontId)
                    .bind(1, userId.toString())
                    .map { row -> mapFont(userId, row) }
                    .firstOrNull()
            } ?: return false
        val fontFile = File(fontsPath, "$userId/${font.filename}")
        if (!fontFile.delete()) fontLogger.warn("Could not delete file: ${fontFile.absolutePath}")
        jdbi.useHandle<Exception> { h ->
            h
                .createUpdate("DELETE FROM user_fonts WHERE id = ? AND user_id = ?")
                .bind(0, fontId)
                .bind(1, userId.toString())
                .execute()
        }
        return true
    }

    fun getFontFile(
        userId: UUID,
        filename: String,
    ): File? {
        val safe = File(filename).name
        val f = File(fontsPath, "$userId/$safe")
        return if (f.exists() && f.isFile) f else null
    }

    private fun mapFont(
        userId: UUID,
        row: org.jdbi.v3.core.result.RowView,
    ): UserFont {
        val createdAt =
            row.getColumn("created_at", String::class.java).let { v ->
                try {
                    Instant.parse(v)
                } catch (_: Exception) {
                    LocalDateTime
                        .parse(
                            v,
                            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss[.SSSSSS][.SSSSS][.SSSS][.SSS][.SS][.S]"),
                        ).toInstant(ZoneOffset.UTC)
                }
            }
        val filename = row.getColumn("filename", String::class.java)
        return UserFont(
            id = row.getColumn("id", String::class.java),
            userId = userId,
            filename = filename,
            originalName = row.getColumn("original_name", String::class.java),
            fileSize = row.getColumn("file_size", java.lang.Long::class.java)?.toLong() ?: 0L,
            createdAt = createdAt,
            url = "/fonts/$userId/$filename",
        )
    }
}
