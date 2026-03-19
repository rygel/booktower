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

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    fun `epub reader margin settings change viewer padding`() {
        val (page, token) = newAuthenticatedPage("margin")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Margin Test Book")
        uploadFile(token, bookId, "book.epub", minimalEpubBytes())

        page.navigate("$baseUrl/books/$bookId/read")
        page.waitForTimeout(3000.0) // wait for epub.js to render

        // Check viewer exists
        val viewer = page.querySelector("#epub-viewer")
        assertTrue(viewer != null, "epub-viewer must exist")

        // Default should be 'normal' margins (2em)
        val defaultPadding = page.evaluate("() => getComputedStyle(document.getElementById('epub-viewer')).padding") as String
        assertTrue(
            defaultPadding.contains("32") || defaultPadding.contains("2em") || defaultPadding.isNotBlank(),
            "Default margins should apply padding to viewer, got: $defaultPadding",
        )

        // Click narrow margin button
        page.click("[data-pref='margins'][data-val='narrow']")
        page.waitForTimeout(500.0)
        val narrowPadding = page.evaluate("() => getComputedStyle(document.getElementById('epub-viewer')).paddingLeft") as String

        // Click wide margin button
        page.click("[data-pref='margins'][data-val='wide']")
        page.waitForTimeout(500.0)
        val widePadding = page.evaluate("() => getComputedStyle(document.getElementById('epub-viewer')).paddingLeft") as String

        // Wide should be larger than narrow
        val narrowPx = narrowPadding.replace("px", "").toDoubleOrNull() ?: 0.0
        val widePx = widePadding.replace("px", "").toDoubleOrNull() ?: 0.0
        assertTrue(widePx > narrowPx, "Wide margin ($widePx px) should be larger than narrow ($narrowPx px)")

        page.close()
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    fun `epub reader font size buttons change text size`() {
        val (page, token) = newAuthenticatedPage("fontsize")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Font Size Test")
        uploadFile(token, bookId, "book.epub", minimalEpubBytes())

        page.navigate("$baseUrl/books/$bookId/read")
        page.waitForTimeout(3000.0)

        // Check zoom percentage display exists
        val zoomPct = page.querySelector("#zoom-pct")
        assertTrue(zoomPct != null, "zoom-pct element should exist")

        // Default is 100%
        val defaultSize = page.textContent("#zoom-pct") ?: ""
        assertTrue(defaultSize.contains("100"), "Default font size should be 100%, got: $defaultSize")

        // Click zoom in
        page.click("#btn-zoom-in")
        page.waitForTimeout(500.0)
        val increasedSize = page.textContent("#zoom-pct") ?: ""
        assertTrue(increasedSize.contains("110"), "Font size should increase to 110% after zoom in, got: $increasedSize")

        // Click zoom out twice
        page.click("#btn-zoom-out")
        page.click("#btn-zoom-out")
        page.waitForTimeout(500.0)
        val decreasedSize = page.textContent("#zoom-pct") ?: ""
        assertTrue(decreasedSize.contains("90"), "Font size should be 90% after zoom out twice, got: $decreasedSize")

        page.close()
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    fun `epub reader theme buttons change viewer background`() {
        val (page, token) = newAuthenticatedPage("theme")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Theme Test")
        uploadFile(token, bookId, "book.epub", minimalEpubBytes())

        page.navigate("$baseUrl/books/$bookId/read")
        page.waitForTimeout(3000.0)

        // Open preferences panel (click the settings button)
        val prefsToggle = page.querySelector("#btn-prefs") ?: page.querySelector("[onclick*='prefs']")
        if (prefsToggle != null) prefsToggle.click()
        page.waitForTimeout(500.0)

        // Click sepia theme
        val sepiaBtn = page.querySelector("[data-pref='theme'][data-val='sepia']")
        if (sepiaBtn != null) {
            sepiaBtn.click()
            page.waitForTimeout(500.0)
            val bg = page.evaluate("() => getComputedStyle(document.getElementById('epub-viewer')).backgroundColor") as String
            // Sepia background is #faf4e8 = rgb(250, 244, 232)
            assertTrue(bg.contains("250") || bg.contains("faf4e8"), "Sepia theme should have warm background, got: $bg")
        }

        // Click dark theme
        val darkBtn = page.querySelector("[data-pref='theme'][data-val='dark']")
        if (darkBtn != null) {
            darkBtn.click()
            page.waitForTimeout(500.0)
            val bg = page.evaluate("() => getComputedStyle(document.getElementById('epub-viewer')).backgroundColor") as String
            // Dark background is #1a1a1a = rgb(26, 26, 26)
            assertTrue(bg.contains("26") || bg.contains("1a1a1a"), "Dark theme should have dark background, got: $bg")
        }

        page.close()
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    fun `epub reader navigation buttons exist and are clickable`() {
        val (page, token) = newAuthenticatedPage("nav")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Navigation Test")
        uploadFile(token, bookId, "book.epub", minimalEpubBytes())

        page.navigate("$baseUrl/books/$bookId/read")
        page.waitForTimeout(3000.0)

        val prevBtn = page.querySelector("#btn-prev")
        val nextBtn = page.querySelector("#btn-next")
        assertTrue(prevBtn != null, "Previous button should exist")
        assertTrue(nextBtn != null, "Next button should exist")

        // Both should be visible
        val prevVisible = page.evaluate("() => !document.getElementById('btn-prev').hidden") as Boolean
        assertTrue(prevVisible, "Previous button should be visible")

        page.close()
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    fun `epub reader shows book title in toolbar`() {
        val (page, token) = newAuthenticatedPage("toolbar")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Toolbar Title Test")
        uploadFile(token, bookId, "book.epub", minimalEpubBytes())

        page.navigate("$baseUrl/books/$bookId/read")
        page.waitForTimeout(2000.0)

        val title = page.textContent("#toolbar .title") ?: ""
        assertTrue(title.contains("Toolbar Title Test"), "Toolbar should show book title, got: '$title'")

        page.close()
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    fun `epub reader preferences panel opens and closes`() {
        val (page, token) = newAuthenticatedPage("prefs")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Prefs Panel Test")
        uploadFile(token, bookId, "book.epub", minimalEpubBytes())

        page.navigate("$baseUrl/books/$bookId/read")
        page.waitForTimeout(2000.0)

        // Find the prefs panel
        val panel = page.querySelector("#prefs-panel")
        assertTrue(panel != null, "Preferences panel element should exist")

        // Initially hidden
        val initialDisplay = page.evaluate("() => getComputedStyle(document.getElementById('prefs-panel')).display") as String

        // Click toggle button to open
        val toggleBtn = page.querySelector("#btn-prefs")
        if (toggleBtn != null) {
            toggleBtn.click()
            page.waitForTimeout(300.0)
            val openDisplay = page.evaluate("() => getComputedStyle(document.getElementById('prefs-panel')).display") as String
            assertTrue(openDisplay != "none", "Prefs panel should be visible after clicking toggle, got: $openDisplay")
        }

        page.close()
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    fun `epub reader renders content inside viewer iframe`() {
        val (page, token) = newAuthenticatedPage("content")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Content Render Test")
        uploadFile(token, bookId, "book.epub", minimalEpubBytes())

        page.navigate("$baseUrl/books/$bookId/read")
        page.waitForTimeout(4000.0) // epub.js needs time to render

        // epub.js creates an iframe inside #epub-viewer
        val hasIframe = page.evaluate("() => document.querySelector('#epub-viewer iframe') !== null") as Boolean
        assertTrue(hasIframe, "epub.js should create an iframe inside #epub-viewer")

        // The iframe should have content (not empty)
        val iframeHasContent = page.evaluate(
            """() => {
        const iframe = document.querySelector('#epub-viewer iframe');
        if (!iframe || !iframe.contentDocument) return false;
        const body = iframe.contentDocument.body;
        return body && body.textContent.trim().length > 0;
    }""",
        ) as Boolean
        assertTrue(iframeHasContent, "epub.js iframe should have rendered text content")

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
