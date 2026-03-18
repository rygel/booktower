package org.booktower.services

import com.github.junrar.Archive
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipFile

private val logger = LoggerFactory.getLogger("booktower.ComicService")

private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")

/** Reads pages from CBZ (ZIP) and CBR (RAR) comic archives. */
class ComicService {
    data class PageInfo(
        val index: Int,
        val name: String,
        val contentType: String,
    )

    /** Returns sorted list of image entry names inside the archive. */
    fun listPages(filePath: String): List<PageInfo> {
        val file = File(filePath)
        if (!file.exists() || !file.isFile) return emptyList()
        return when (file.extension.lowercase()) {
            "cbz" -> listCbzPages(file)
            "cbr" -> listCbrPages(file)
            else -> emptyList()
        }
    }

    /** Returns the raw bytes for a single page (0-indexed). */
    fun getPage(
        filePath: String,
        pageIndex: Int,
    ): ByteArray? {
        val file = File(filePath)
        if (!file.exists() || !file.isFile) return null
        return when (file.extension.lowercase()) {
            "cbz" -> getCbzPage(file, pageIndex)
            "cbr" -> getCbrPage(file, pageIndex)
            else -> null
        }
    }

    fun getPageCount(filePath: String): Int = listPages(filePath).size

    // ── CBZ (ZIP) ─────────────────────────────────────────────────────────────

    private fun listCbzPages(file: File): List<PageInfo> =
        try {
            ZipFile(file).use { zip ->
                zip
                    .entries()
                    .asSequence()
                    .filter { !it.isDirectory && it.name.substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS }
                    .sortedBy { it.name }
                    .mapIndexed { idx, entry -> PageInfo(idx, entry.name, mimeOf(entry.name)) }
                    .toList()
            }
        } catch (e: Exception) {
            logger.warn("Failed to list CBZ pages: ${file.name}", e)
            emptyList()
        }

    private fun getCbzPage(
        file: File,
        pageIndex: Int,
    ): ByteArray? {
        return try {
            ZipFile(file).use { zip ->
                val entries =
                    zip
                        .entries()
                        .asSequence()
                        .filter { !it.isDirectory && it.name.substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS }
                        .sortedBy { it.name }
                        .toList()
                val entry = entries.getOrNull(pageIndex) ?: return null
                zip.getInputStream(entry).readBytes()
            }
        } catch (e: Exception) {
            logger.warn("Failed to read CBZ page $pageIndex: ${file.name}", e)
            null
        }
    }

    // ── CBR (RAR via junrar) ──────────────────────────────────────────────────

    private fun listCbrPages(file: File): List<PageInfo> =
        try {
            Archive(file).use { rar ->
                rar.fileHeaders
                    .filter { !it.isDirectory && it.fileName.substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS }
                    .sortedBy { it.fileName }
                    .mapIndexed { idx, hdr -> PageInfo(idx, hdr.fileName, mimeOf(hdr.fileName)) }
            }
        } catch (e: Exception) {
            logger.warn("Failed to list CBR pages: ${file.name} — ${e.message}")
            emptyList()
        }

    private fun getCbrPage(
        file: File,
        pageIndex: Int,
    ): ByteArray? {
        return try {
            Archive(file).use { rar ->
                val headers =
                    rar.fileHeaders
                        .filter { !it.isDirectory && it.fileName.substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS }
                        .sortedBy { it.fileName }
                val hdr = headers.getOrNull(pageIndex) ?: return null
                val out = ByteArrayOutputStream()
                rar.extractFile(hdr, out)
                out.toByteArray()
            }
        } catch (e: Exception) {
            logger.warn("Failed to read CBR page $pageIndex: ${file.name}", e)
            null
        }
    }

    private fun mimeOf(name: String): String =
        when (name.substringAfterLast('.', "").lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            else -> "image/jpeg"
        }
}
