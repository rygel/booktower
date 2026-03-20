package org.booktower.integration

import org.booktower.config.Json
import org.booktower.models.BookDto
import org.booktower.models.BookListDto
import org.http4k.core.Body
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * End-to-end tests verifying the full upload-to-read pipeline for all media types:
 * EPUBs, comics (CBZ), and audiobooks (MP3 chapters).
 *
 * Uses synthetic test data — no external network calls. Safe to run in every CI build.
 */
class MediaPipelineIntegrationTest : IntegrationTestBase() {
    /** Uploads bytes to a book's file endpoint. */
    private fun uploadFile(
        token: String,
        bookId: String,
        filename: String,
        bytes: ByteArray,
    ) {
        val response =
            app(
                Request(Method.POST, "/api/books/$bookId/upload")
                    .header("Cookie", "token=$token")
                    .header("X-Filename", filename)
                    .header("Content-Type", "application/octet-stream")
                    .body(Body(ByteArrayInputStream(bytes), bytes.size.toLong())),
            )
        assertTrue(
            response.status == Status.OK || response.status == Status.CREATED || response.status == Status.NO_CONTENT,
            "Upload should succeed, got ${response.status}: ${response.bodyString()}",
        )
    }

    /** Uploads an MP3 chapter to a book. */
    private fun uploadChapter(
        token: String,
        bookId: String,
        trackIndex: Int,
        filename: String,
        bytes: ByteArray,
    ) {
        val response =
            app(
                Request(Method.POST, "/api/books/$bookId/chapters")
                    .header("Cookie", "token=$token")
                    .header("X-Filename", filename)
                    .header("X-Track-Index", trackIndex.toString())
                    .header("Content-Type", "application/octet-stream")
                    .body(Body(ByteArrayInputStream(bytes), bytes.size.toLong())),
            )
        assertTrue(
            response.status == Status.OK || response.status == Status.CREATED || response.status == Status.NO_CONTENT,
            "Chapter upload should succeed, got ${response.status}: ${response.bodyString()}",
        )
    }

    /** Builds a minimal but structurally valid EPUB zip. */
    private fun minimalEpubBytes(): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zip ->
            // mimetype — must be first, uncompressed
            zip.setMethod(ZipOutputStream.STORED)
            val mimeBytes = "application/epub+zip".toByteArray()
            val mimeEntry = ZipEntry("mimetype")
            mimeEntry.size = mimeBytes.size.toLong()
            mimeEntry.compressedSize = mimeBytes.size.toLong()
            mimeEntry.crc = CRC32().also { it.update(mimeBytes) }.value
            zip.putNextEntry(mimeEntry)
            zip.write(mimeBytes)
            zip.closeEntry()

            zip.setMethod(ZipOutputStream.DEFLATED)

            zip.putNextEntry(ZipEntry("META-INF/container.xml"))
            zip.write(
                """<?xml version="1.0"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles>
    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
  </rootfiles>
</container>""".toByteArray(),
            )
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("OEBPS/content.opf"))
            zip.write(
                """<?xml version="1.0" encoding="utf-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="2.0" unique-identifier="bookid">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:title>Test EPUB Book</dc:title>
    <dc:identifier id="bookid">test-epub-e2e</dc:identifier>
  </metadata>
  <manifest>
    <item id="chapter1" href="chapter1.xhtml" media-type="application/xhtml+xml"/>
    <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
  </manifest>
  <spine toc="ncx">
    <itemref idref="chapter1"/>
  </spine>
</package>""".toByteArray(),
            )
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("OEBPS/chapter1.xhtml"))
            zip.write(
                """<?xml version="1.0" encoding="utf-8"?>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.1//EN" "http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
  <head><title>Chapter One</title></head>
  <body><h1>Chapter One</h1><p>This is a test paragraph for the E2E pipeline test.</p></body>
</html>""".toByteArray(),
            )
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("OEBPS/toc.ncx"))
            zip.write(
                """<?xml version="1.0" encoding="utf-8"?>
<ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
  <head><meta name="dtb:uid" content="test-epub-e2e"/></head>
  <docTitle><text>Test EPUB Book</text></docTitle>
  <navMap>
    <navPoint id="navPoint-1" playOrder="1">
      <navLabel><text>Chapter One</text></navLabel>
      <content src="chapter1.xhtml"/>
    </navPoint>
  </navMap>
</ncx>""".toByteArray(),
            )
            zip.closeEntry()
        }
        return baos.toByteArray()
    }

    /** Builds a minimal CBZ (ZIP containing JPEG images). */
    private fun minimalCbzBytes(pageCount: Int = 3): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zip ->
            repeat(pageCount) { i ->
                val imgBytes = createTestJpeg(100, 150, i)
                zip.putNextEntry(ZipEntry("page_${(i + 1).toString().padStart(3, '0')}.jpg"))
                zip.write(imgBytes)
                zip.closeEntry()
            }
        }
        return baos.toByteArray()
    }

    /** Creates a minimal JPEG image with a unique color per page. */
    private fun createTestJpeg(
        width: Int,
        height: Int,
        seed: Int,
    ): ByteArray {
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        // Different color per page so they're distinguishable
        g.color = java.awt.Color((seed * 80 + 50) % 256, (seed * 60 + 100) % 256, (seed * 40 + 150) % 256)
        g.fillRect(0, 0, width, height)
        g.color = java.awt.Color.WHITE
        g.drawString("Page ${seed + 1}", 10, 30)
        g.dispose()
        val baos = ByteArrayOutputStream()
        ImageIO.write(img, "JPEG", baos)
        return baos.toByteArray()
    }

    /**
     * Creates a minimal valid MP3 frame (MPEG1 Layer 3, 128kbps, 44100Hz, stereo).
     * Contains a single silent frame — just enough for the endpoint to accept it.
     */
    private fun minimalMp3Bytes(): ByteArray {
        // MP3 frame header: 0xFF 0xFB = MPEG1, Layer 3, 128kbps, 44100Hz, stereo
        // Frame size for 128kbps at 44100Hz = 417 bytes (including header)
        val frameSize = 417
        val frame = ByteArray(frameSize)
        frame[0] = 0xFF.toByte() // Sync byte 1
        frame[1] = 0xFB.toByte() // Sync byte 2 + MPEG1, Layer3, no CRC
        frame[2] = 0x90.toByte() // 128kbps, 44100Hz
        frame[3] = 0x00.toByte() // Stereo, no padding
        // Rest is zeros (silence)
        return frame
    }

    // ── EPUB Pipeline ─────────────────────────────────────────────────────

    @Test
    fun `EPUB upload and download pipeline works end to end`() {
        val token = registerAndGetToken("epub_e2e")
        val libId = createLibrary(token, "EPUB Test Library")
        val bookId = createBook(token, libId, "Test EPUB Book")

        // Upload EPUB
        val epubBytes = minimalEpubBytes()
        uploadFile(token, bookId, "test-book.epub", epubBytes)

        // Verify file download works
        val downloadResponse =
            app(
                Request(Method.GET, "/api/books/$bookId/file")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.OK, downloadResponse.status, "Should be able to download the uploaded EPUB")
        val downloadedBytes = downloadResponse.body.stream.readBytes()
        assertEquals(epubBytes.size, downloadedBytes.size, "Downloaded EPUB should match uploaded size")

        // Verify book metadata was updated with file info
        val bookResponse =
            app(
                Request(Method.GET, "/api/books/$bookId")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.OK, bookResponse.status)
        val book = Json.mapper.readValue(bookResponse.bodyString(), BookDto::class.java)
        assertTrue(book.fileSize > 0, "Book fileSize should be updated after upload (got ${book.fileSize})")
    }

    @Test
    fun `EPUB download without upload returns 404`() {
        val token = registerAndGetToken("epub_no_file")
        val libId = createLibrary(token, "EPUB Empty Library")
        val bookId = createBook(token, libId, "Book Without File")

        val response =
            app(
                Request(Method.GET, "/api/books/$bookId/file")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.NOT_FOUND, response.status, "Should return 404 for book without uploaded file")
    }

    // ── Comic (CBZ) Pipeline ──────────────────────────────────────────────

    @Test
    fun `CBZ upload and comic page serving works end to end`() {
        val token = registerAndGetToken("comic_e2e")
        val libId = createLibrary(token, "Comic Test Library")
        val bookId = createBook(token, libId, "Test Comic")

        // Upload CBZ
        val cbzBytes = minimalCbzBytes(3)
        uploadFile(token, bookId, "test-comic.cbz", cbzBytes)

        // Verify file download works
        val downloadResponse =
            app(
                Request(Method.GET, "/api/books/$bookId/file")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.OK, downloadResponse.status, "Should be able to download the uploaded CBZ")

        // Verify comic pages listing works
        val pagesResponse =
            app(
                Request(Method.GET, "/api/books/$bookId/comic/pages")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.OK, pagesResponse.status, "Comic pages endpoint should return OK")
        val pagesBody = pagesResponse.bodyString()
        assertTrue(pagesBody.contains("\"pageCount\":3"), "Pages response should report 3 pages, got: $pagesBody")

        // Verify individual page serving works
        val pageResponse =
            app(
                Request(Method.GET, "/api/books/$bookId/comic/0")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.OK, pageResponse.status, "Should be able to serve individual comic page")
        val pageBytes = pageResponse.body.stream.readBytes()
        assertTrue(pageBytes.size > 100, "Comic page should have image content (got ${pageBytes.size} bytes)")
        assertTrue(
            pageResponse.header("Content-Type")?.contains("image") == true,
            "Comic page should be served as image, got: ${pageResponse.header("Content-Type")}",
        )
    }

    @Test
    fun `CBZ page out of range returns 404`() {
        val token = registerAndGetToken("comic_oob")
        val libId = createLibrary(token, "Comic OOB Library")
        val bookId = createBook(token, libId, "Comic OOB Test")

        uploadFile(token, bookId, "test-comic.cbz", minimalCbzBytes(2))

        // Page index 99 doesn't exist
        val response =
            app(
                Request(Method.GET, "/api/books/$bookId/comic/99")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.NOT_FOUND, response.status, "Out-of-range page should return 404")
    }

    // ── Audiobook (MP3 chapters) Pipeline ─────────────────────────────────

    @Test
    fun `audiobook chapter upload and listing works end to end`() {
        val token = registerAndGetToken("audio_e2e")
        val libId = createLibrary(token, "Audiobook Test Library")
        val bookId = createBook(token, libId, "Test Audiobook")

        // Upload chapters
        val mp3Bytes = minimalMp3Bytes()
        uploadChapter(token, bookId, 0, "chapter-01.mp3", mp3Bytes)
        uploadChapter(token, bookId, 1, "chapter-02.mp3", mp3Bytes)

        // Verify chapters listing works
        val chaptersResponse =
            app(
                Request(Method.GET, "/api/books/$bookId/chapters")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.OK, chaptersResponse.status, "Chapters endpoint should return OK")

        // Verify individual chapter streaming works
        val streamResponse =
            app(
                Request(Method.GET, "/api/books/$bookId/chapters/0")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.OK, streamResponse.status, "Should be able to stream individual chapter")
        val streamedBytes = streamResponse.body.stream.readBytes()
        assertTrue(streamedBytes.isNotEmpty(), "Streamed chapter should have content")
    }

    // ── Cross-cutting concerns ────────────────────────────────────────────

    @Test
    fun `unauthenticated file access returns 401`() {
        val token = registerAndGetToken("auth_test")
        val libId = createLibrary(token, "Auth Test Library")
        val bookId = createBook(token, libId, "Auth Test Book")
        uploadFile(token, bookId, "test.epub", minimalEpubBytes())

        // Try accessing without auth
        val response = app(Request(Method.GET, "/api/books/$bookId/file"))
        assertEquals(Status.UNAUTHORIZED, response.status, "Unauthenticated file access should be rejected")
    }

    @Test
    fun `book with file shows correct fileSize in listing`() {
        val token = registerAndGetToken("filesize_test")
        val libId = createLibrary(token, "FileSize Test Library")
        val bookId1 = createBook(token, libId, "Book With File")
        val bookId2 = createBook(token, libId, "Book Without File")

        val epubBytes = minimalEpubBytes()
        uploadFile(token, bookId1, "test.epub", epubBytes)

        // List books and verify fileSize
        val booksResponse =
            app(
                Request(Method.GET, "/api/books?pageSize=50")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.OK, booksResponse.status)
        val books = Json.mapper.readValue(booksResponse.bodyString(), BookListDto::class.java).getBooks()

        val withFile = books.first { it.id == bookId1 }
        val withoutFile = books.first { it.id == bookId2 }

        assertTrue(withFile.fileSize > 0, "Uploaded book should report fileSize > 0")
        assertEquals(0, withoutFile.fileSize, "Book without file should report fileSize = 0")
    }

    @Test
    fun `multiple format types can coexist in same library`() {
        val token = registerAndGetToken("multiformat")
        val libId = createLibrary(token, "Multi-Format Library")

        val epubBookId = createBook(token, libId, "EPUB Book")
        val comicBookId = createBook(token, libId, "Comic Book")

        uploadFile(token, epubBookId, "book.epub", minimalEpubBytes())
        uploadFile(token, comicBookId, "comic.cbz", minimalCbzBytes(2))

        // Both should be downloadable
        val epubDl = app(Request(Method.GET, "/api/books/$epubBookId/file").header("Cookie", "token=$token"))
        val comicDl = app(Request(Method.GET, "/api/books/$comicBookId/file").header("Cookie", "token=$token"))

        assertEquals(Status.OK, epubDl.status, "EPUB download should work")
        assertEquals(Status.OK, comicDl.status, "CBZ download should work")

        // Comic pages should work for the CBZ
        val pagesResp = app(Request(Method.GET, "/api/books/$comicBookId/comic/pages").header("Cookie", "token=$token"))
        assertEquals(Status.OK, pagesResp.status, "Comic pages should work for CBZ book")

        // Comic pages for EPUB should return empty or error
        val epubPagesResp = app(Request(Method.GET, "/api/books/$epubBookId/comic/pages").header("Cookie", "token=$token"))
        // Either 404 or empty list is acceptable
        assertTrue(
            epubPagesResp.status == Status.OK || epubPagesResp.status == Status.NOT_FOUND,
            "Comic pages for non-comic should return OK (empty) or 404",
        )
    }
}
