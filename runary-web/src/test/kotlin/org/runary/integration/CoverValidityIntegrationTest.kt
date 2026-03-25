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
import org.runary.TestFixture
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Verifies that cover images are served correctly — valid JPEG, proper headers, correct URLs.
 *
 * Most tests bypass the slow PDF→JPEG extraction pipeline by placing a pre-built JPEG
 * directly into the covers directory. Only [uploading a second PDF regenerates cover]
 * exercises the full upload+extraction path.
 */
class CoverValidityIntegrationTest : IntegrationTestBase() {
    companion object {
        /** A small valid JPEG, built once per JVM. */
        private val TEST_JPEG: ByteArray by lazy {
            val img = BufferedImage(200, 300, BufferedImage.TYPE_INT_RGB)
            val g = img.createGraphics()
            g.color = Color.DARK_GRAY
            g.fillRect(0, 0, 200, 300)
            g.color = Color.WHITE
            g.drawString("Test Cover", 50, 150)
            g.dispose()
            ByteArrayOutputStream().also { ImageIO.write(img, "JPEG", it) }.toByteArray()
        }

        /** A minimal valid PDF, built once per JVM. */
        private val TEST_PDF: ByteArray by lazy { buildTestPdf(2) }

        private val TEST_PDF_3_PAGES: ByteArray by lazy { buildTestPdf(3) }

        private fun buildTestPdf(pageCount: Int): ByteArray {
            val doc = PDDocument()
            repeat(pageCount) { i ->
                val page = PDPage(PDRectangle.A4)
                doc.addPage(page)
                if (i == 0) {
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
    }

    /** Place a pre-built cover JPEG directly in the covers directory, bypassing PDF extraction. */
    private fun placeCover(bookId: String) {
        val coversDir = File(TestFixture.config.storage.coversPath)
        coversDir.mkdirs()
        File(coversDir, "$bookId.jpg").writeBytes(TEST_JPEG)
        // Update DB so the app knows a cover exists
        TestFixture.database.getJdbi().useHandle<Exception> { h ->
            h
                .createUpdate("UPDATE books SET cover_path = ? WHERE id = ?")
                .bind(0, "$bookId.jpg")
                .bind(1, bookId)
                .execute()
        }
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
        placeCover(bookId)

        val resp = app(Request(Method.GET, "/covers/$bookId.jpg"))
        assertEquals(Status.OK, resp.status, "Cover should be available")

        val bytes = resp.body.stream.readBytes()
        assertTrue(bytes.size >= 3, "Cover should have at least 3 bytes")
        assertEquals(0xFF.toByte(), bytes[0], "First byte should be 0xFF (JPEG SOI)")
        assertEquals(0xD8.toByte(), bytes[1], "Second byte should be 0xD8 (JPEG SOI)")
        assertEquals(0xFF.toByte(), bytes[2], "Third byte should be 0xFF (JPEG APP marker)")
    }

    @Test
    fun `generated cover has reasonable file size (not trivially small)`() {
        val token = registerAndGetToken("cov2")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Cover Size Book")
        placeCover(bookId)

        val resp = app(Request(Method.GET, "/covers/$bookId.jpg"))
        assertEquals(Status.OK, resp.status)
        val bytes = resp.body.stream.readBytes()
        assertTrue(bytes.size >= 1024, "Cover should be at least 1 KB, got ${bytes.size} bytes")
    }

    // ── Content-Type and caching ──────────────────────────────────────────────

    @Test
    fun `cover is served with correct JPEG Content-Type header`() {
        val token = registerAndGetToken("cov3")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Cover Type Book")
        placeCover(bookId)

        val resp = app(Request(Method.GET, "/covers/$bookId.jpg"))
        assertEquals(Status.OK, resp.status)
        val contentType = resp.header("Content-Type") ?: ""
        assertTrue(contentType.contains("image/jpeg"), "Content-Type should be image/jpeg, got: $contentType")
    }

    @Test
    fun `cover has cache-control header for browser caching`() {
        val token = registerAndGetToken("cov4")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Cover Cache Book")
        placeCover(bookId)

        val resp = app(Request(Method.GET, "/covers/$bookId.jpg"))
        assertEquals(Status.OK, resp.status)
        val cacheControl = resp.header("Cache-Control") ?: ""
        assertTrue(cacheControl.contains("max-age"), "Cover should include Cache-Control: max-age, got: $cacheControl")
    }

    // ── Cover URL reflected in book DTO ──────────────────────────────────────

    @Test
    fun `book DTO coverUrl points to served cover after extraction`() {
        val token = registerAndGetToken("cov5")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Cover DTO Book")
        placeCover(bookId)

        val bookResp = app(Request(Method.GET, "/api/books/$bookId").header("Cookie", "token=$token"))
        assertEquals(Status.OK, bookResp.status)
        val body = bookResp.bodyString()
        assertTrue(body.contains("/covers/$bookId.jpg"), "book DTO should contain coverUrl pointing to /covers/$bookId.jpg")
    }

    @Test
    fun `cover URL in book detail HTML page points to served cover`() {
        val token = registerAndGetToken("cov6")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Cover HTML Book")
        placeCover(bookId)

        val pageResp = app(Request(Method.GET, "/books/$bookId").header("Cookie", "token=$token"))
        assertEquals(Status.OK, pageResp.status)
        val html = pageResp.bodyString()
        assertTrue(html.contains("/covers/$bookId.jpg"), "Book detail page should render <img> pointing to /covers/$bookId.jpg")
    }

    // ── Upload + extraction round-trip (exercises the full pipeline once) ────

    @Test
    fun `uploading a second PDF regenerates and serves a new valid cover`() {
        val token = registerAndGetToken("cov7")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Cover Regen Book")

        uploadPdf(token, bookId, TEST_PDF)
        Thread.sleep(2500)

        val firstResp = app(Request(Method.GET, "/covers/$bookId.jpg"))
        assertEquals(Status.OK, firstResp.status)

        uploadPdf(token, bookId, TEST_PDF_3_PAGES)
        Thread.sleep(2500)

        val secondResp = app(Request(Method.GET, "/covers/$bookId.jpg"))
        assertEquals(Status.OK, secondResp.status)
        val secondBytes = secondResp.body.stream.readBytes()

        assertEquals(0xFF.toByte(), secondBytes[0])
        assertEquals(0xD8.toByte(), secondBytes[1])
        assertTrue(secondBytes.size >= 1024, "Regenerated cover should be at least 1 KB")
    }
}
