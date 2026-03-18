package org.booktower.services

import org.slf4j.LoggerFactory
import java.io.File

private val logger = LoggerFactory.getLogger("booktower.FilenameMetadataService")

data class FilenameMetadata(
    val title: String,
    val author: String?,
    val series: String?,
    val seriesIndex: Float?,
)

/**
 * Extracts book metadata from common filename patterns without touching the filesystem.
 *
 * Supported patterns (case-insensitive, extension stripped):
 *  1. "Author - Title"                              → author + title
 *  2. "Author - Title (Series N)"                   → author + title + series + index
 *  3. "Author - Series N - Title"                   → author + series + index + title
 *  4. "Title (Series N)"                            → title + series + index
 *  5. "Title"                                       → title only
 *
 * Where N is an optional float/integer (e.g. "1", "1.5", "02").
 */
object FilenameMetadataService {
    // "Series N" or "Series, Book N" inside parentheses or brackets
    private val SERIES_IN_PARENS =
        Regex(
            """[\(\[]\s*(.+?)[,\s]+(?:book\s+)?(\d+(?:\.\d+)?)\s*[\)\]]""",
            RegexOption.IGNORE_CASE,
        )

    // "Author - Title" separator
    private val AUTHOR_TITLE_SEP = Regex("""\s+[-–]\s+""")

    // "Series N - Title" where series is e.g. "Dune 2" or "Wheel of Time 01"
    private val SERIES_INDEX_INLINE =
        Regex(
            """^(.*?)\s+(\d+(?:\.\d+)?)\s*$""",
        )

    fun extract(filePath: String): FilenameMetadata {
        val name = File(filePath).nameWithoutExtension.trim()
        return parse(name).also {
            logger.debug("Filename '{}' → title='{}' author='{}' series='{}' idx={}", name, it.title, it.author, it.series, it.seriesIndex)
        }
    }

    internal fun parse(raw: String): FilenameMetadata {
        var remainder = raw.trim()

        // 1. Extract series + index from parentheses/brackets
        var series: String? = null
        var seriesIndex: Float? = null
        val seriesMatch = SERIES_IN_PARENS.find(remainder)
        if (seriesMatch != null) {
            series = seriesMatch.groupValues[1].trim()
            seriesIndex = seriesMatch.groupValues[2].toFloatOrNull()
            remainder = remainder.removeRange(seriesMatch.range).trim()
        }

        // 2. Split on " - " to separate author from title
        val parts = AUTHOR_TITLE_SEP.split(remainder)

        return when {
            parts.size >= 3 -> {
                // "Author - Series N - Title"  (series already captured above) or "A - B - C"
                val author = parts[0].trim().takeIf { it.isNotBlank() }
                val titleCandidate = parts.last().trim()
                // Middle parts might encode series+index if not already found
                if (series == null) {
                    val middleMatch = SERIES_INDEX_INLINE.find(parts.dropLast(1).drop(1).joinToString(" - "))
                    if (middleMatch != null) {
                        series = middleMatch.groupValues[1].trim().takeIf { it.isNotBlank() }
                        seriesIndex = middleMatch.groupValues[2].toFloatOrNull()
                    }
                }
                FilenameMetadata(title = titleCandidate.ifBlank { raw }, author = author, series = series, seriesIndex = seriesIndex)
            }

            parts.size == 2 -> {
                // "Author - Title"
                val author = parts[0].trim().takeIf { it.isNotBlank() }
                val title = parts[1].trim().ifBlank { raw }
                FilenameMetadata(title = title, author = author, series = series, seriesIndex = seriesIndex)
            }

            else -> {
                // No author separator — remainder is the title
                FilenameMetadata(title = remainder.ifBlank { raw }, author = null, series = series, seriesIndex = seriesIndex)
            }
        }
    }
}
