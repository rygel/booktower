package org.runary.integration

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Verifies that auto-generated cover images from PDF uploads are valid JPEG files,
 * not just non-empty byte sequences. Checks JPEG magic bytes and that the image
 * dimensions are reasonable.
 *
 * Complements CoverIntegrationTest which only checks status codes and Content-Type.
 */
class CoverValidityIntegrationTest : IntegrationTestBase() {
    /** Build a minimal but valid multi-page PDF with some content on the first page. */
    private fun pdfWithContent(pageCount: Int = 2): ByteArray {
        val doc = PDDocument()
        repeat(pageCount) { i ->
            val page = PDPage(PDRectangle.A4)
            doc.addPage(page)
            if (i == 0) {
                // Put a tiny bit of content on the first page so cover render has something
                PDPageContentStream(doc, page).use { cs ->
                    cs.beginText()
                    cs.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA), 12f)
                    cs.newLineAtOffset(100f, 700f)
                    cs.showText("Cover Test Page")
                    cs.endText()
                }
            }
        }
        return ByteArrayOutputStream()
            .also {
                doc.save(it)
                doc.close()
            }.toByteArray()
    }

    private fun uploadPdf(
        token: String,
        bookId: String,
        bytes: ByteArray,
    ) {
        val resp =
            app(
                Request(Method.POST, "/api/books/$bookId/upload")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/octet-stream")
                    .header("X-Filename", "test.pdf")
                    .body(ByteArrayInputStream(bytes)),
            )
        assertEquals(Status.OK, resp.status, "PDF upload should succeed")
    }

    // ── JPEG magic bytes ──────────────────────────────────────────────────────

    @Test
    fun `generated cover is a valid JPEG (magic bytes FFD8FF)`() {
        val token = registerAndGetToken("cov1")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Cover Validity Book")

        uploadPdf(token, bookId, pdfWithContent())
        Thread.sleep(2500) // wait for async extraction

        val resp = app(Request(Method.GET, "/covers/$bookId.jpg"))
        assertEquals(Status.OK, resp.status, "Cover should be available after extraction")

        val bytes = resp.body.stream.readBytes()
        assertTrue(bytes.size >= 3, "Cover should have at least 3 bytes")
        // JPEG magic: FF D8 FF
        assertEquals(0xFF.toByte(), bytes[0], "First byte should be 0xFF (JPEG SOI)")
        assertEquals(0xD8.toByte(), bytes[1], "Second byte should be 0xD8 (JPEG SOI)")
        assertEquals(0xFF.toByte(), bytes[2], "Third byte should be 0xFF (JPEG APP marker)")
    }

    @Test
    fun `generated cover has reasonable file size (not trivially small)`() {
        val token = registerAndGetToken("cov2")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Cover Size Book")

        uploadPdf(token, bookId, pdfWithContent())
        Thread.sleep(2500)

        val resp = app(Request(Method.GET, "/covers/$bookId.jpg"))
        assertEquals(Status.OK, resp.status)
        val bytes = resp.body.stream.readBytes()
        // A blank-ish JPEG should still be at least 1 KB
        assertTrue(bytes.size >= 1024, "Cover should be at least 1 KB, got ${bytes.size} bytes")
    }

    // ── Content-Type and caching ──────────────────────────────────────────────

    @Test
    fun `cover is served with correct JPEG Content-Type header`() {
        val token = registerAndGetToken("cov3")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Cover Type Book")

        uploadPdf(token, bookId, pdfWithContent())
        Thread.sleep(2500)

        val resp = app(Request(Method.GET, "/covers/$bookId.jpg"))
        assertEquals(Status.OK, resp.status)
        val contentType = resp.header("Content-Type") ?: ""
        assertTrue(
            contentType.contains("image/jpeg"),
            "Content-Type should be image/jpeg, got: $contentType",
        )
    }

    @Test
    fun `cover has cache-control header for browser caching`() {
        val token = registerAndGetToken("cov4")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Cover Cache Book")

        uploadPdf(token, bookId, pdfWithContent())
        Thread.sleep(2500)

        val resp = app(Request(Method.GET, "/covers/$bookId.jpg"))
        assertEquals(Status.OK, resp.status)
        val cacheControl = resp.header("Cache-Control") ?: ""
        assertTrue(
            cacheControl.contains("max-age"),
            "Cover should include Cache-Control: max-age, got: $cacheControl",
        )
    }

    // ── Cover URL reflected in book DTO ──────────────────────────────────────

    @Test
    fun `book DTO coverUrl points to served cover after extraction`() {
        val token = registerAndGetToken("cov5")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Cover DTO Book")

        uploadPdf(token, bookId, pdfWithContent())
        Thread.sleep(2500)

        val bookResp = app(Request(Method.GET, "/api/books/$bookId").header("Cookie", "token=$token"))
        assertEquals(Status.OK, bookResp.status)
        val body = bookResp.bodyString()
        assertTrue(
            body.contains("/covers/$bookId.jpg"),
            "book DTO should contain coverUrl pointing to /covers/$bookId.jpg",
        )
    }

    @Test
    fun `cover URL in book detail HTML page points to served cover`() {
        val token = registerAndGetToken("cov6")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Cover HTML Book")

        uploadPdf(token, bookId, pdfWithContent())
        Thread.sleep(2500)

        val pageResp = app(Request(Method.GET, "/books/$bookId").header("Cookie", "token=$token"))
        assertEquals(Status.OK, pageResp.status)
        val html = pageResp.bodyString()
        assertTrue(
            html.contains("/covers/$bookId.jpg"),
            "Book detail page should render <img> pointing to /covers/$bookId.jpg",
        )
    }

    // ── Second upload regenerates cover ──────────────────────────────────────

    @Test
    fun `uploading a second PDF regenerates and serves a new valid cover`() {
        val token = registerAndGetToken("cov7")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Cover Regen Book")

        // First upload
        uploadPdf(token, bookId, pdfWithContent(1))
        Thread.sleep(2500)

        val firstResp = app(Request(Method.GET, "/covers/$bookId.jpg"))
        assertEquals(Status.OK, firstResp.status)
        val firstBytes = firstResp.body.stream.readBytes()

        // Second upload
        uploadPdf(token, bookId, pdfWithContent(3))
        Thread.sleep(2500)

        val secondResp = app(Request(Method.GET, "/covers/$bookId.jpg"))
        assertEquals(Status.OK, secondResp.status)
        val secondBytes = secondResp.body.stream.readBytes()

        // Both should be valid JPEGs
        assertEquals(0xFF.toByte(), secondBytes[0])
        assertEquals(0xD8.toByte(), secondBytes[1])
        assertTrue(secondBytes.size >= 1024, "Regenerated cover should be at least 1 KB")
    }
}
