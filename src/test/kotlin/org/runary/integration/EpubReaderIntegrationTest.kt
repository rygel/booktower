package org.runary.integration

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EpubReaderIntegrationTest : IntegrationTestBase() {
    @Test
    fun `reader page requires authentication`() {
        val token = registerAndGetToken("er1")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val response = app(Request(Method.GET, "/books/$bookId/read"))
        // Should redirect to login (no cookie)
        assertEquals(Status.SEE_OTHER, response.status)
        assertTrue(response.header("Location")?.contains("login") == true)
    }

    @Test
    fun `reader page returns 200 for authenticated user`() {
        val token = registerAndGetToken("er2")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val response = app(Request(Method.GET, "/books/$bookId/read").header("Cookie", "token=$token"))
        assertEquals(Status.OK, response.status)
    }

    @Test
    fun `reader page returns 404 for nonexistent book`() {
        val token = registerAndGetToken("er3")

        val response =
            app(
                Request(Method.GET, "/books/00000000-0000-0000-0000-000000000000/read")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.NOT_FOUND, response.status)
    }

    @Test
    fun `reader page shows no-file state when no file uploaded`() {
        val token = registerAndGetToken("er4")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val body = app(Request(Method.GET, "/books/$bookId/read").header("Cookie", "token=$token")).bodyString()
        assertTrue(body.contains("id=\"no-file\""), "Should show no-file div when no file uploaded")
        assertFalse(body.contains("id=\"pdf-canvas\""), "Should not show PDF canvas when no file uploaded")
        assertFalse(body.contains("id=\"epub-viewer\""), "Should not show EPUB viewer when no file uploaded")
    }

    @Test
    fun `reader page contains book title in toolbar`() {
        val token = registerAndGetToken("er5")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "My Test EPUB Book")

        val body = app(Request(Method.GET, "/books/$bookId/read").header("Cookie", "token=$token")).bodyString()
        assertTrue(body.contains("My Test EPUB Book"), "Toolbar should contain the book title")
    }

    @Test
    fun `reader page has back link to book detail`() {
        val token = registerAndGetToken("er6")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val body = app(Request(Method.GET, "/books/$bookId/read").header("Cookie", "token=$token")).bodyString()
        assertTrue(body.contains("/books/$bookId"), "Should have link back to book detail page")
    }

    @Test
    fun `reader page has jszip loaded before epub-js`() {
        val token = registerAndGetToken("erjz")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)
        // Upload a minimal valid EPUB so the page renders the epub reader block
        val epubBytes = minimalEpub()
        app(
            Request(Method.POST, "/api/books/$bookId/upload")
                .header("Cookie", "token=$token")
                .header("X-Filename", "book.epub")
                .header("Content-Type", "application/octet-stream")
                .body(org.http4k.core.Body(ByteArrayInputStream(epubBytes), epubBytes.size.toLong())),
        )

        val html = app(Request(Method.GET, "/books/$bookId/read").header("Cookie", "token=$token")).bodyString()
        val jszipIdx = html.indexOf("jszip")
        val epubjsIdx = html.indexOf("epub.js")
        assertTrue(jszipIdx > 0, "jszip script should be present in epub reader page")
        assertTrue(epubjsIdx > 0, "epub.js script should be present in epub reader page")
        assertTrue(jszipIdx < epubjsIdx, "jszip must appear before epub.js in the HTML")
    }

    @Test
    fun `epub reader uses URLSearchParams not FormData for progress save`() {
        val token = registerAndGetToken("ersp")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)
        val epubBytes = minimalEpub()
        app(
            Request(Method.POST, "/api/books/$bookId/upload")
                .header("Cookie", "token=$token")
                .header("X-Filename", "book.epub")
                .header("Content-Type", "application/octet-stream")
                .body(org.http4k.core.Body(ByteArrayInputStream(epubBytes), epubBytes.size.toLong())),
        )

        val html = app(Request(Method.GET, "/books/$bookId/read").header("Cookie", "token=$token")).bodyString()
        // Extract just the EPUB reader script block (between EPUB_URL and the next @elseif/@endif block marker)
        val epubStart = html.indexOf("EPUB_URL")
        assertTrue(epubStart > 0, "Should contain EPUB_URL constant")
        val epubBlock = html.substring(epubStart, minOf(epubStart + 25000, html.length))
        assertTrue(epubBlock.contains("URLSearchParams"), "EPUB reader progress save must use URLSearchParams")
        assertFalse(epubBlock.contains("new FormData()"), "EPUB reader must not use FormData for progress (multipart breaks req.form())")
    }

    @Test
    fun `progress endpoint accepts urlencoded content type`() {
        val token = registerAndGetToken("erul")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val resp =
            app(
                Request(Method.POST, "/ui/books/$bookId/progress")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .body("currentPage=55"),
            )
        assertEquals(Status.OK, resp.status)
    }

    @Test
    fun `reader page returns 404 for book belonging to different user`() {
        val token1 = registerAndGetToken("er7a")
        val token2 = registerAndGetToken("er7b")
        val libId = createLibrary(token1)
        val bookId = createBook(token1, libId)

        val response = app(Request(Method.GET, "/books/$bookId/read").header("Cookie", "token=$token2"))
        assertEquals(Status.NOT_FOUND, response.status)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────────

    private fun minimalEpub(): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zip ->
            val mimeBytes = "application/epub+zip".toByteArray()
            zip.setMethod(ZipOutputStream.STORED)
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
                """<?xml version="1.0"?><container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""".toByteArray(),
            )
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("OEBPS/content.opf"))
            zip.write(
                """<?xml version="1.0"?><package xmlns="http://www.idpf.org/2007/opf" version="2.0" unique-identifier="id"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>Test</dc:title><dc:identifier id="id">t1</dc:identifier></metadata><manifest><item id="c1" href="c1.xhtml" media-type="application/xhtml+xml"/><item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/></manifest><spine toc="ncx"><itemref idref="c1"/></spine></package>""".toByteArray(),
            )
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("OEBPS/c1.xhtml"))
            zip.write(
                """<?xml version="1.0"?><html xmlns="http://www.w3.org/1999/xhtml"><head><title>C1</title></head><body><p>Test</p></body></html>""".toByteArray(),
            )
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("OEBPS/toc.ncx"))
            zip.write(
                """<?xml version="1.0"?><ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1"><head><meta name="dtb:uid" content="t1"/></head><docTitle><text>Test</text></docTitle><navMap><navPoint id="n1" playOrder="1"><navLabel><text>C1</text></navLabel><content src="c1.xhtml"/></navPoint></navMap></ncx>""".toByteArray(),
            )
            zip.closeEntry()
        }
        return baos.toByteArray()
    }
}
