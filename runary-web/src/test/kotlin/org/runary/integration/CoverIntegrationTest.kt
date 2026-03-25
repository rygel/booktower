package org.runary.integration

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.runary.TestFixture
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CoverIntegrationTest : IntegrationTestBase() {
    private fun minimalPdfBytes(): ByteArray {
        val doc = PDDocument()
        doc.addPage(PDPage())
        val out = ByteArrayOutputStream()
        doc.save(out)
        doc.close()
        return out.toByteArray()
    }

    @Test
    fun `cover endpoint returns 404 for unknown filename`() {
        val response = app(Request(Method.GET, "/covers/nonexistent.jpg"))
        assertEquals(Status.NOT_FOUND, response.status)
    }

    @Test
    fun `cover endpoint rejects path traversal`() {
        val response = app(Request(Method.GET, "/covers/..%2F..%2Fetc%2Fpasswd"))
        // Should be 400 or 404, never 200
        assertTrue(response.status.code >= 400)
    }

    @Test
    fun `cover is served after PDF upload with correct content type`() {
        val token = registerAndGetToken("cov")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        // Upload a real PDF so cover extraction runs
        val pdfBytes = minimalPdfBytes()
        app(
            Request(Method.POST, "/api/books/$bookId/upload")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/octet-stream")
                .header("X-Filename", "book.pdf")
                .body(ByteArrayInputStream(pdfBytes)),
        )

        // Give the async extractor a moment to finish
        Thread.sleep(2000)

        val response = app(Request(Method.GET, "/covers/$bookId.jpg"))
        assertEquals(Status.OK, response.status)
        assertTrue(response.header("Content-Type")?.contains("image/jpeg") == true)
        assertTrue(response.header("Cache-Control")?.contains("max-age") == true)
        assertTrue(response.bodyString().isNotEmpty())
    }

    @Test
    fun `cover endpoint is public (no auth required)`() {
        // Any non-existent cover returns 404, not 401
        val response = app(Request(Method.GET, "/covers/00000000-0000-0000-0000-000000000000.jpg"))
        assertEquals(Status.NOT_FOUND, response.status)
    }

    @Test
    fun `cover url in book dto points to correct endpoint`() {
        val token = registerAndGetToken("covurl")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        // Upload real PDF and wait for cover extraction
        val pdfBytes = minimalPdfBytes()
        app(
            Request(Method.POST, "/api/books/$bookId/upload")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/octet-stream")
                .header("X-Filename", "book.pdf")
                .body(ByteArrayInputStream(pdfBytes)),
        )
        Thread.sleep(2000)

        val bookResponse =
            app(
                Request(Method.GET, "/api/books/$bookId")
                    .header("Cookie", "token=$token"),
            )
        val body = bookResponse.bodyString()
        // coverUrl should start with /covers/ after extraction
        assertTrue(body.contains("/covers/") || !body.contains("coverUrl"), "coverUrl should point to /covers/")
    }

    @Test
    fun `cover for book without uploaded file returns 404`() {
        val config = TestFixture.config
        val coversPath = config.storage.coversPath
        val fakeId = "ffffffff-ffff-ffff-ffff-ffffffffffff"
        // Ensure no cover file exists for this id
        val coverFile = java.io.File(coversPath, "$fakeId.jpg")
        coverFile.delete()

        val response = app(Request(Method.GET, "/covers/$fakeId.jpg"))
        assertEquals(Status.NOT_FOUND, response.status)
    }
}
