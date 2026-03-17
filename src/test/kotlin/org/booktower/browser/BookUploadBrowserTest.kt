package org.booktower.browser

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import kotlin.test.assertTrue

/**
 * Browser tests for book file upload and the resulting reader routing.
 * Verifies that after uploading an EPUB/PDF, the reader page correctly
 * reflects the format and serves the right JS reader type.
 */
@Tag("browser")
class BookUploadBrowserTest : BrowserTestBase() {

    @Test
    fun `book detail page shows upload section before any file is uploaded`() {
        val (page, token) = newAuthenticatedPage("bupload")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Upload Test Book")

        page.navigate("$baseUrl/books/$bookId")
        page.waitForTimeout(500.0)

        val body = page.content()
        assertTrue(
            body.contains("Upload") || body.contains("upload"),
            "Book detail page should show upload section before a file is uploaded",
        )
        page.close()
    }

    @Test
    fun `after epub upload the read button is present on book detail page`() {
        val (page, token) = newAuthenticatedPage("bupepub")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "EPUB Upload Test")
        uploadFile(token, bookId, "book.epub", minimalEpubBytes())

        page.navigate("$baseUrl/books/$bookId")
        page.waitForTimeout(500.0)

        val body = page.content()
        assertTrue(
            body.contains("/read") || body.contains("Read"),
            "After EPUB upload the book detail page should show a Read button/link",
        )
        page.close()
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    fun `epub reader page shows epub-viewer after upload`() {
        val (page, token) = newAuthenticatedPage("buprd")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Reader Viewer Test")
        uploadFile(token, bookId, "book.epub", minimalEpubBytes())

        page.navigate("$baseUrl/books/$bookId/read")
        page.waitForTimeout(1000.0)

        assertTrue(
            page.querySelector("#epub-viewer") != null,
            "EPUB reader page should contain #epub-viewer element",
        )
        page.close()
    }

    @Test
    fun `reader page for book with no file shows no-file state`() {
        val (page, token) = newAuthenticatedPage("bnoup")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "No File Book")

        page.navigate("$baseUrl/books/$bookId/read")
        page.waitForTimeout(500.0)

        val body = page.content()
        assertTrue(
            body.contains("no-file") || body.contains("No file") || body.contains("Upload"),
            "Reader page with no uploaded file should show the no-file state",
        )
        // Must NOT show any reader UI
        assertTrue(
            page.querySelector("#epub-viewer") == null,
            "epub-viewer must not be present when no file uploaded",
        )
        assertTrue(
            page.querySelector("#pdf-canvas") == null,
            "pdf-canvas must not be present when no file uploaded",
        )
        page.close()
    }

    @Test
    fun `epub reader scripts load from vendored paths not CDN`() {
        val (page, token) = newAuthenticatedPage("bvend")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Vendor Path Test")
        uploadFile(token, bookId, "book.epub", minimalEpubBytes())

        val failedCdnRequests = mutableListOf<String>()
        page.onResponse { resp ->
            val url = resp.url()
            if ((url.contains("cdn.jsdelivr.net") || url.contains("cdnjs.cloudflare.com") ||
                    url.contains("unpkg.com")) && !resp.ok()
            ) {
                failedCdnRequests.add(url)
            }
        }
        // Intercept any CDN requests — there should be none at all
        val cdnRequests = mutableListOf<String>()
        page.onRequest { req ->
            val url = req.url()
            if (url.contains("cdn.jsdelivr.net") || url.contains("cdnjs.cloudflare.com") ||
                url.contains("unpkg.com")
            ) {
                cdnRequests.add(url)
            }
        }

        page.navigate("$baseUrl/books/$bookId/read")
        page.waitForTimeout(1000.0)

        assertTrue(
            cdnRequests.isEmpty(),
            "No CDN requests should be made — all assets are vendored. Got: $cdnRequests",
        )
        page.close()
    }
}
