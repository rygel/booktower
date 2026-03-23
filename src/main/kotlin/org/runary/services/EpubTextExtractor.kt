package org.runary.services

import org.slf4j.LoggerFactory
import java.io.File
import java.util.zip.ZipFile

private val epubExtractLog = LoggerFactory.getLogger("runary.EpubTextExtractor")

object EpubTextExtractor {
    private val HTML_TAG_RE = Regex("<[^>]+>")
    private val MULTI_SPACE_RE = Regex("\\s{2,}")
    private val CONTENT_EXTS = setOf("xhtml", "html", "htm")

    fun extract(file: File): String? =
        try {
            ZipFile(file).use { zip ->
                zip
                    .entries()
                    .asSequence()
                    .filter { !it.isDirectory }
                    .filter { it.name.substringAfterLast('.').lowercase() in CONTENT_EXTS }
                    .sortedBy { it.name }
                    .joinToString(" ") { entry ->
                        zip
                            .getInputStream(entry)
                            .bufferedReader(Charsets.UTF_8)
                            .readText()
                            .replace(HTML_TAG_RE, " ")
                            .replace(MULTI_SPACE_RE, " ")
                            .trim()
                    }.take(2_000_000)
            }
        } catch (e: Exception) {
            epubExtractLog.warn("EPUB text extraction failed for ${file.name}: ${e.message}")
            null
        }
}
