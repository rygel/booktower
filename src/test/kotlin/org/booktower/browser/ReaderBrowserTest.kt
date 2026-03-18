package org.booktower.browser

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Browser-level tests for the reader page using Playwright.
 *
 * These tests catch JS runtime errors that HTTP-level integration tests cannot see:
 * missing script dependencies (e.g. JSZip before epub.js), sandboxing errors,
 * broken fetch calls, and DOM state after JS initialisation.
 *
 * Run with: mvn test -P browser-tests -Dtest="ReaderBrowserTest"
 */
@Tag("browser")
class ReaderBrowserTest : BrowserTestBase() {
    // ── Script dependency ordering (HTML-level, no browser execution needed) ─────

    @Test
    fun `epub reader page loads jszip before epubjs`() {
        val token = registerAndGetToken("sdo")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Script Order Test")
        uploadFile(token, bookId, "book.epub", minimalEpubBytes())

        val html =
            app(
                org.http4k.core
                    .Request(org.http4k.core.Method.GET, "/books/$bookId/read")
                    .header("Cookie", "token=$token"),
            ).bodyString()

        val jszipPos = html.indexOf("jszip")
        val epubPos = html.indexOf("epub.js")
        assertTrue(jszipPos > 0, "jszip.min.js script tag should be present")
        assertTrue(epubPos > 0, "epub.js script tag should be present")
        assertTrue(jszipPos < epubPos, "jszip must be loaded before epub.js (found jszip at $jszipPos, epub.js at $epubPos)")
    }

    @Test
    fun `epub reader page uses URLSearchParams for progress save`() {
        val token = registerAndGetToken("ups")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "URLSearchParams Test")
        uploadFile(token, bookId, "book.epub", minimalEpubBytes())

        val html =
            app(
                org.http4k.core
                    .Request(org.http4k.core.Method.GET, "/books/$bookId/read")
                    .header("Cookie", "token=$token"),
            ).bodyString()

        // The EPUB reader block should use URLSearchParams, not FormData, for progress saves
        val epubScriptStart = html.indexOf("EPUB_URL")
        assertTrue(epubScriptStart > 0, "Should have EPUB reader script block")
        val epubBlock = html.substring(epubScriptStart, epubScriptStart + 2000)
        assertTrue(
            epubBlock.contains("URLSearchParams"),
            "Progress save must use URLSearchParams (not FormData) for application/x-www-form-urlencoded",
        )
        assertFalse(epubBlock.contains("new FormData()"), "FormData sends multipart which req.form() cannot read")
    }

    @Test
    fun `progress endpoint accepts urlencoded body`() {
        val token = registerAndGetToken("ule")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Progress URL-Encoded Test")

        val resp =
            app(
                org.http4k.core
                    .Request(org.http4k.core.Method.POST, "/ui/books/$bookId/progress")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .body("currentPage=42"),
            )
        assertEquals(200, resp.status.code, "Progress endpoint should accept urlencoded body")
    }

    // ── Playwright browser tests ──────────────────────────────────────────────────

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    fun `epub reader page loads without JS errors`() {
        val (page, token) = newAuthenticatedPage("epuberr")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "EPUB Error Test")
        uploadFile(token, bookId, "book.epub", minimalEpubBytes())

        val errors = mutableListOf<String>()
        page.onConsoleMessage { msg ->
            if (msg.type() == "error") errors.add(msg.text())
        }

        page.navigate("$baseUrl/books/$bookId/read")
        page.waitForTimeout(3000.0) // give epub.js time to initialise

        val criticalErrors =
            errors.filter { err ->
                err.contains("JSZip", ignoreCase = true) ||
                    err.contains("srcdoc", ignoreCase = true) ||
                    err.contains("sandboxed", ignoreCase = true) ||
                    err.contains("allow-scripts", ignoreCase = true)
            }
        assertTrue(
            criticalErrors.isEmpty(),
            "EPUB reader should have no critical JS errors, but got: $criticalErrors\nAll errors: $errors",
        )

        page.close()
    }

    @Test
    fun `epub reader page has JSZip defined globally`() {
        val (page, token) = newAuthenticatedPage("jszipg")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "JSZip Global Test")
        uploadFile(token, bookId, "book.epub", minimalEpubBytes())

        page.navigate("$baseUrl/books/$bookId/read")
        page.waitForTimeout(1000.0)

        val jszipDefined = page.evaluate("() => typeof window.JSZip !== 'undefined'") as Boolean
        assertTrue(jszipDefined, "window.JSZip must be defined before epub.js initialises")

        page.close()
    }

    @Test
    fun `epub reader page has epub-viewer element in DOM`() {
        val (page, token) = newAuthenticatedPage("epubdom")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "EPUB DOM Test")
        uploadFile(token, bookId, "book.epub", minimalEpubBytes())

        page.navigate("$baseUrl/books/$bookId/read")
        page.waitForTimeout(500.0)

        val viewer = page.querySelector("#epub-viewer")
        assertTrue(viewer != null, "epub-viewer element must exist in the DOM")

        page.close()
    }

    @Test
    fun `pdf reader page loads without JS errors`() {
        val (page, token) = newAuthenticatedPage("pdferr")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "PDF Error Test")
        // Upload a minimal valid 1-page PDF
        uploadFile(token, bookId, "book.pdf", minimalPdfBytes())

        val errors = mutableListOf<String>()
        page.onConsoleMessage { msg ->
            if (msg.type() == "error") errors.add(msg.text())
        }

        page.navigate("$baseUrl/books/$bookId/read")
        page.waitForTimeout(2000.0)

        val criticalErrors =
            errors.filter { err ->
                !err.contains("favicon") &&
                    // ignore favicon 404s
                    !err.contains("icon-") &&
                    err.isNotBlank()
            }
        // PDF.js worker errors (missing worker) are expected in headless; filter them
        val unexpectedErrors =
            criticalErrors.filter { err ->
                !err.contains("worker", ignoreCase = true) &&
                    !err.contains("fetch", ignoreCase = true)
            }
        assertTrue(
            unexpectedErrors.isEmpty(),
            "PDF reader should have no unexpected JS errors, but got: $unexpectedErrors",
        )

        page.close()
    }

    @Test
    fun `login page renders without JS errors`() {
        val page = browser.newPage()
        val errors = mutableListOf<String>()
        page.onConsoleMessage { msg ->
            if (msg.type() == "error") errors.add(msg.text())
        }

        page.navigate("$baseUrl/auth/login")
        page.waitForTimeout(500.0)

        val criticalErrors =
            errors.filter { err ->
                !err.contains("favicon") && !err.contains("icon-")
            }
        assertTrue(
            criticalErrors.isEmpty(),
            "Login page should have no JS errors, but got: $criticalErrors",
        )

        // Verify the login form is present
        val form = page.querySelector("form")
        assertTrue(form != null, "Login page must have a form element")

        page.close()
    }

    @Test
    fun `notification bell is present when logged in`() {
        val (page, token) = newAuthenticatedPage("notifbell")
        val libId = createLibrary(token)
        createBook(token, libId)

        page.navigate("$baseUrl/")
        page.waitForTimeout(500.0)

        // The bell button is present for authenticated users (see layout.kte)
        val bell = page.querySelector("[aria-label='Notifications'], #notif-btn, button[onclick*='toggleNotifPanel']")
        assertTrue(bell != null, "Notification bell button must be present in the header when logged in")

        page.close()
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    fun `reader page progress save does not return 400`() {
        val (page, token) = newAuthenticatedPage("progsave")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Progress Save Test")
        uploadFile(token, bookId, "book.epub", minimalEpubBytes())

        val failedRequests = mutableListOf<String>()
        page.onResponse { resp ->
            if (resp.url().contains("/progress") && !resp.ok()) {
                failedRequests.add("${resp.url()} -> ${resp.status()}")
            }
        }

        page.navigate("$baseUrl/books/$bookId/read")
        // Wait for epub.js to initialise and fire the first relocated event
        page.waitForTimeout(5000.0)

        assertTrue(
            failedRequests.isEmpty(),
            "Progress save should not fail (expected 200, not 400/403), but got: $failedRequests",
        )

        page.close()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────────

    /** Builds a minimal structurally valid 1-page PDF. */
    private fun minimalPdfBytes(): ByteArray {
        val body = """1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj
2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj
3 0 obj<</Type/Page/MediaBox[0 0 612 792]/Parent 2 0 R>>endobj
"""
        val xref = body.length + 9 // %PDF-1.4\n
        val pdf = """%PDF-1.4
$body
xref
0 4
0000000000 65535 f
0000000009 00000 n
0000000058 00000 n
0000000115 00000 n
trailer<</Size 4/Root 1 0 R>>
startxref
$xref
%%EOF"""
        return pdf.toByteArray(Charsets.US_ASCII)
    }
}
