package org.runary.services

import org.runary.models.FetchedMetadata
import org.slf4j.LoggerFactory
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

private val logger = LoggerFactory.getLogger("runary.SidecarMetadataService")

/**
 * Reads metadata from sidecar files (.opf or .nfo) that sit next to a book file.
 *
 * OPF (Open Packaging Format) is the standard used by Calibre and most e-book tools.
 * NFO is a simple XML-like format used by some media managers.
 */
object SidecarMetadataService {
    /**
     * Looks for a sidecar file next to [bookFilePath] and parses it.
     * Returns null if no sidecar exists or parsing fails.
     */
    fun read(bookFilePath: String): FetchedMetadata? {
        val bookFile = File(bookFilePath)
        val dir = bookFile.parentFile ?: return null
        val base = bookFile.nameWithoutExtension

        // Priority: same-name .opf, then same-name .nfo, then any metadata.opf in the same dir
        val candidates =
            listOf(
                File(dir, "$base.opf"),
                File(dir, "$base.nfo"),
                File(dir, "metadata.opf"),
                File(dir, "book.nfo"),
            )

        for (sidecar in candidates) {
            if (sidecar.exists() && sidecar.isFile) {
                val result =
                    when (sidecar.extension.lowercase()) {
                        "opf" -> parseOpf(sidecar)
                        "nfo" -> parseNfo(sidecar)
                        else -> null
                    }
                if (result != null) {
                    logger.info("Loaded sidecar metadata from ${sidecar.name} for $bookFilePath")
                    return result
                }
            }
        }
        return null
    }

    // ── OPF parser ────────────────────────────────────────────────────────────

    internal fun parseOpf(file: File): FetchedMetadata? {
        return try {
            val doc =
                DocumentBuilderFactory
                    .newInstance()
                    .also { it.isNamespaceAware = true }
                    .newDocumentBuilder()
                    .parse(file)
            doc.documentElement.normalize()

            fun tag(name: String): String? {
                val nodes = doc.getElementsByTagNameNS("*", name)
                return if (nodes.length > 0) {
                    nodes
                        .item(0)
                        .textContent
                        ?.trim()
                        ?.takeIf { it.isNotBlank() }
                } else {
                    null
                }
            }

            fun tags(name: String): List<String> {
                val nodes = doc.getElementsByTagNameNS("*", name)
                return (0 until nodes.length).mapNotNull {
                    nodes
                        .item(it)
                        .textContent
                        ?.trim()
                        ?.takeIf { s -> s.isNotBlank() }
                }
            }

            // Series info lives in meta elements: <meta name="calibre:series" content="..."/>
            fun metaContent(metaName: String): String? {
                val metas = doc.getElementsByTagNameNS("*", "meta")
                for (i in 0 until metas.length) {
                    val el = metas.item(i) as? Element ?: continue
                    if (el.getAttribute("name") == metaName) {
                        return el.getAttribute("content").trim().takeIf { it.isNotBlank() }
                    }
                }
                return null
            }

            val title = tag("title")
            val authors = tags("creator").ifEmpty { null }
            val description = tag("description")
            val isbn = tags("identifier").firstOrNull { it.length >= 10 && it.all { c -> c.isDigit() || c == '-' || c == 'X' } }
            val publisher = tag("publisher")
            val date = tag("date")?.take(10) // yyyy-mm-dd or yyyy

            FetchedMetadata(
                title = title,
                author = authors?.joinToString(", "),
                description = description,
                isbn = isbn,
                publisher = publisher,
                publishedDate = date?.take(4),
                source = "opf",
            )
        } catch (e: Exception) {
            logger.warn("Failed to parse OPF ${file.name}: ${e.message}")
            null
        }
    }

    // ── NFO parser ────────────────────────────────────────────────────────────

    internal fun parseNfo(file: File): FetchedMetadata? {
        return try {
            val doc =
                DocumentBuilderFactory
                    .newInstance()
                    .newDocumentBuilder()
                    .parse(file)
            doc.documentElement.normalize()

            fun tag(name: String): String? {
                val nodes = doc.getElementsByTagName(name)
                return if (nodes.length > 0) {
                    nodes
                        .item(0)
                        .textContent
                        ?.trim()
                        ?.takeIf { it.isNotBlank() }
                } else {
                    null
                }
            }

            FetchedMetadata(
                title = tag("title"),
                author = tag("author") ?: tag("creator") ?: tag("artist"),
                description = tag("plot") ?: tag("description") ?: tag("outline"),
                isbn = tag("isbn"),
                publisher = tag("publisher") ?: tag("studio"),
                publishedDate = tag("year") ?: tag("premiered")?.take(4),
                source = "nfo",
            )
        } catch (e: Exception) {
            logger.warn("Failed to parse NFO ${file.name}: ${e.message}")
            null
        }
    }
}
