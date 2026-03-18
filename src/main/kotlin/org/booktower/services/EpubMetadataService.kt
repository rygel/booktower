package org.booktower.services

import org.jdbi.v3.core.Jdbi
import org.slf4j.LoggerFactory
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import java.time.Instant
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

private val logger = LoggerFactory.getLogger("booktower.EpubMetadataService")
private const val DC_NS = "http://purl.org/dc/elements/1.1/"
private const val OPF_MEDIA_TYPE = "application/oebps-package+xml"

data class EpubMetadata(
    val title: String?,
    val author: String?,
    val description: String?,
    val coverPath: String?,
)

class EpubMetadataService(
    private val jdbi: Jdbi,
    private val coversPath: String,
) {
    private val executor: ExecutorService =
        Executors.newSingleThreadExecutor { r ->
            Thread(r, "epub-metadata").also { it.isDaemon = true }
        }

    fun submitAsync(
        bookId: String,
        epubFile: File,
    ) {
        executor.submit { extractAndStore(bookId, epubFile) }
    }

    fun extractAndStore(
        bookId: String,
        epubFile: File,
    ): EpubMetadata {
        return try {
            ZipFile(epubFile).use { zip ->
                val opfPath = findOpfPath(zip)
                if (opfPath == null) {
                    logger.warn("No OPF file found in EPUB for book $bookId")
                    return@use EpubMetadata(null, null, null, null)
                }

                val opfDir = opfPath.substringBeforeLast('/', "")
                val opfEntry = zip.getEntry(opfPath)
                if (opfEntry == null) {
                    logger.warn("OPF entry not found in ZIP: $opfPath for book $bookId")
                    return@use EpubMetadata(null, null, null, null)
                }

                val factory =
                    DocumentBuilderFactory.newInstance().also {
                        it.isNamespaceAware = true
                        it.isValidating = false
                        it.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
                    }
                val doc = factory.newDocumentBuilder().parse(zip.getInputStream(opfEntry))

                val title = dcText(doc, "title")
                val author = dcText(doc, "creator")
                val description = dcText(doc, "description")?.let { stripHtml(it) }
                val coverPath = extractCover(zip, doc, opfDir, bookId)

                val metadata = EpubMetadata(title, author, description, coverPath)
                persistMetadata(bookId, metadata)

                logger.info("EPUB metadata extracted for book $bookId: title=$title, author=$author")
                metadata
            }
        } catch (e: Exception) {
            logger.warn("Failed to extract EPUB metadata for book $bookId: ${e.message}")
            EpubMetadata(null, null, null, null)
        }
    }

    fun shutdown(timeoutSeconds: Long = 30) {
        executor.shutdown()
        if (!executor.awaitTermination(timeoutSeconds, TimeUnit.SECONDS)) {
            logger.warn("EPUB metadata executor did not terminate in ${timeoutSeconds}s, forcing shutdown")
            executor.shutdownNow()
        }
    }

    // ── Private ────────────────────────────────────────────────────────────────

    /** Reads META-INF/container.xml to find the OPF package document path. */
    private fun findOpfPath(zip: ZipFile): String? {
        val containerEntry = zip.getEntry("META-INF/container.xml") ?: return null
        val factory = DocumentBuilderFactory.newInstance().also { it.isNamespaceAware = true }
        val doc = factory.newDocumentBuilder().parse(zip.getInputStream(containerEntry))
        val rootfiles = doc.getElementsByTagName("rootfile")
        for (i in 0 until rootfiles.length) {
            val item = rootfiles.item(i) as? Element ?: continue
            val mediaType = item.getAttribute("media-type")
            if (mediaType == OPF_MEDIA_TYPE || mediaType.isBlank()) {
                val path = item.getAttribute("full-path").trim()
                if (path.isNotBlank()) return path
            }
        }
        // Fallback: any .opf entry in the ZIP
        return zip
            .entries()
            .asSequence()
            .firstOrNull { it.name.endsWith(".opf", ignoreCase = true) }
            ?.name
    }

    /** Extract cover image bytes from the EPUB and save to coversPath. Returns filename or null. */
    private fun extractCover(
        zip: ZipFile,
        doc: Document,
        opfDir: String,
        bookId: String,
    ): String? {
        val coverHref = findCoverHref(doc) ?: return null

        // Resolve href relative to the OPF directory
        val fullPath = if (opfDir.isEmpty()) coverHref else "$opfDir/$coverHref"
        val coverEntry = zip.getEntry(fullPath) ?: zip.getEntry(coverHref) ?: return null

        return try {
            val coversDir = File(coversPath)
            if (!coversDir.exists() && !coversDir.mkdirs()) logger.warn("Could not create directory: ${coversDir.absolutePath}")
            val ext =
                coverHref
                    .substringAfterLast('.', "jpg")
                    .lowercase()
                    .let { if (it in setOf("jpg", "jpeg", "png", "webp", "gif")) it else "jpg" }
            val coverFile = File(coversDir, "$bookId.$ext")
            zip.getInputStream(coverEntry).use { input ->
                coverFile.outputStream().use { output -> input.copyTo(output) }
            }
            coverFile.name
        } catch (e: Exception) {
            logger.warn("Failed to save EPUB cover for book $bookId: ${e.message}")
            null
        }
    }

    // Finds the cover image href from the OPF manifest, trying three strategies:
    // 1. EPUB3: manifest item with properties="cover-image"
    // 2. EPUB2: <meta name="cover" content="id"/> -> manifest item by that id
    // 3. Fallback: manifest item with id="cover" or id="cover-image" and an image MIME type
    private fun findCoverHref(doc: Document): String? {
        val items = doc.getElementsByTagName("item")

        // Strategy 1: EPUB3 cover-image property
        for (i in 0 until items.length) {
            val item = items.item(i) as? Element ?: continue
            if (item.getAttribute("properties").split(" ").contains("cover-image")) {
                val href = item.getAttribute("href").trim()
                if (href.isNotBlank()) return href
            }
        }

        // Strategy 2: EPUB2 <meta name="cover" content="id"/>
        val metas = doc.getElementsByTagName("meta")
        for (i in 0 until metas.length) {
            val meta = metas.item(i) as? Element ?: continue
            if (meta.getAttribute("name").equals("cover", ignoreCase = true)) {
                val coverId = meta.getAttribute("content").trim()
                if (coverId.isNotBlank()) {
                    for (j in 0 until items.length) {
                        val item = items.item(j) as? Element ?: continue
                        if (item.getAttribute("id").equals(coverId, ignoreCase = true)) {
                            val href = item.getAttribute("href").trim()
                            if (href.isNotBlank()) return href
                        }
                    }
                }
            }
        }

        // Strategy 3: item id matches "cover" or "cover-image"
        val coverIds = setOf("cover", "cover-image", "cover-img")
        for (i in 0 until items.length) {
            val item = items.item(i) as? Element ?: continue
            val id = item.getAttribute("id").lowercase()
            val mediaType = item.getAttribute("media-type")
            if (id in coverIds && mediaType.startsWith("image/")) {
                val href = item.getAttribute("href").trim()
                if (href.isNotBlank()) return href
            }
        }

        return null
    }

    /** Read a Dublin Core element by local name, trying NS then qualified name then local name. */
    private fun dcText(
        doc: Document,
        localName: String,
    ): String? {
        var nodes = doc.getElementsByTagNameNS(DC_NS, localName)
        if (nodes.length == 0) nodes = doc.getElementsByTagName("dc:$localName")
        if (nodes.length == 0) nodes = doc.getElementsByTagName(localName)
        return nodes
            .item(0)
            ?.textContent
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    /** Strip HTML tags from description text (some EPUBs include markup). */
    private fun stripHtml(text: String): String = text.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim()

    private fun persistMetadata(
        bookId: String,
        metadata: EpubMetadata,
    ) {
        val now = Instant.now().toString()
        jdbi.useHandle<Exception> { handle ->
            val setClauses =
                buildList {
                    add("updated_at = ?")
                    if (metadata.title != null) add("title = ?")
                    if (metadata.author != null) add("author = ?")
                    if (metadata.description != null) add("description = ?")
                    if (metadata.coverPath != null) add("cover_path = ?")
                }
            val sql = "UPDATE books SET ${setClauses.joinToString(", ")} WHERE id = ?"
            val update = handle.createUpdate(sql)
            var idx = 0
            update.bind(idx++, now)
            if (metadata.title != null) update.bind(idx++, metadata.title)
            if (metadata.author != null) update.bind(idx++, metadata.author)
            if (metadata.description != null) update.bind(idx++, metadata.description)
            if (metadata.coverPath != null) update.bind(idx++, metadata.coverPath)
            update.bind(idx, bookId)
            update.execute()
        }
    }
}
