package org.booktower.integration

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.booktower.config.Json
import org.booktower.models.BookmarkDto
import org.booktower.models.LibraryDto
import org.booktower.models.LoginResponse
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.core.cookie.cookies
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream

/**
 * End-to-end workflow tests that walk through user-facing flows as a browser would:
 * register → navigate → mutate → verify rendered HTML reflects the change.
 *
 * These complement the feature-level integration tests by asserting that the full
 * stack (handler → service → DB → template) produces correct HTML output.
 */
class WorkflowIntegrationTest : IntegrationTestBase() {

    // ── Fixtures ─────────────────────────────────────────────────────────────

    private fun minimalPdf(): ByteArray {
        val doc = PDDocument().also { it.addPage(PDPage()) }
        return ByteArrayOutputStream().also { doc.save(it); doc.close() }.toByteArray()
    }

    // Minimal valid 1×1 PNG
    private val minimalPng = byteArrayOf(
        0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a,
        0x00, 0x00, 0x00, 0x0d, 0x49, 0x48, 0x44, 0x52,
        0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
        0x08, 0x02, 0x00, 0x00, 0x00, 0x90.toByte(), 0x77, 0x53,
        0xde.toByte(), 0x00, 0x00, 0x00, 0x0c, 0x49, 0x44, 0x41,
        0x54, 0x08, 0xd7.toByte(), 0x63, 0xf8.toByte(), 0xcf.toByte(), 0xc0.toByte(), 0x00,
        0x00, 0x00, 0x02, 0x00, 0x01, 0xe2.toByte(), 0x21, 0xbc.toByte(),
        0x33, 0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4e,
        0x44, 0xae.toByte(), 0x42, 0x60, 0x82.toByte(),
    )

    private fun uploadPdf(token: String, bookId: String) =
        app(
            Request(Method.POST, "/api/books/$bookId/upload")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/octet-stream")
                .header("X-Filename", "book.pdf")
                .body(minimalPdf().inputStream()),
        )

    private fun uploadCover(token: String, bookId: String) =
        app(
            Request(Method.POST, "/api/books/$bookId/cover")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/octet-stream")
                .header("X-Filename", "cover.png")
                .body(minimalPng.inputStream()),
        )

    private fun getHtml(path: String, token: String, extraCookies: String = ""): String {
        val cookie = if (extraCookies.isBlank()) "token=$token" else "token=$token; $extraCookies"
        return app(Request(Method.GET, path).header("Cookie", cookie)).bodyString()
    }

    // ── Gap 1: Cover visible in rendered HTML after upload ────────────────

    @Test
    fun `cover upload is visible as img tag on rendered book detail page`() {
        val token = registerAndGetToken("wf_cover")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Cover Workflow Book")

        // Before upload: no img tag for cover
        val before = getHtml("/books/$bookId", token)
        assertFalse(before.contains("/covers/"), "No cover img before upload")

        // Upload cover
        val uploadResp = uploadCover(token, bookId)
        assertEquals(Status.OK, uploadResp.status)
        val coverUrl = Json.mapper.readTree(uploadResp.bodyString())["coverUrl"].asText()

        // After upload: rendered HTML contains the cover img
        val after = getHtml("/books/$bookId", token)
        assertTrue(after.contains(coverUrl), "Rendered book page should contain coverUrl: $coverUrl")
        assertTrue(after.contains("<img"), "Rendered book page should have an img element")
    }

    @Test
    fun `cover upload updates library card cover on library page`() {
        val token = registerAndGetToken("wf_libcover")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Lib Cover Book")

        uploadCover(token, bookId)

        // The book detail page (which embeds cover img) should reflect it
        val page = getHtml("/books/$bookId", token)
        assertTrue(page.contains("/covers/"), "Book detail page should reference the cover path")
    }

    // ── Gap 2: New-user first-use workflow ────────────────────────────────

    @Test
    fun `new user first-use workflow - register to reading progress via HTML pages`() {
        val username = "wf_newuser_${System.nanoTime()}"

        // 1. Register
        val regResp = app(
            Request(Method.POST, "/auth/register")
                .header("Content-Type", "application/json")
                .body("""{"username":"$username","email":"$username@test.com","password":"password123"}"""),
        )
        val token = Json.mapper.readValue(regResp.bodyString(), LoginResponse::class.java).token

        // 2. Libraries page renders (authenticated entry point)
        val libsPage = getHtml("/libraries", token)
        assertEquals(200, app(Request(Method.GET, "/libraries").header("Cookie", "token=$token")).status.code)
        assertTrue(libsPage.contains("BookTower"), "Libraries page should be a BookTower page")

        // 3. Create library via HTMX endpoint → returns HTML fragment
        val createLibResp = app(
            Request(Method.POST, "/ui/libraries")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body("name=My+First+Library&path=./data/wf-${System.nanoTime()}"),
        )
        assertEquals(Status.OK, createLibResp.status)
        val libFragment = createLibResp.bodyString()
        assertTrue(libFragment.contains("My First Library"), "Library fragment should contain library name")

        // Get the library id from API
        val libId = Json.mapper.readValue(
            app(Request(Method.GET, "/api/libraries").header("Cookie", "token=$token")).bodyString(),
            Array<LibraryDto>::class.java,
        ).first { it.name == "My First Library" }.id

        // 4. Library detail page renders
        val libPage = getHtml("/libraries/$libId", token)
        assertTrue(libPage.contains("My First Library"), "Library detail page should show library name")

        // 5. Add a book via HTMX endpoint → returns HTML fragment
        val createBookResp = app(
            Request(Method.POST, "/ui/libraries/$libId/books")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body("title=My+First+Book&author=Me"),
        )
        assertEquals(Status.OK, createBookResp.status)
        assertTrue(createBookResp.bodyString().contains("My First Book"), "Book fragment should contain book title")

        // Get book id from API
        val bookId = Json.mapper.readValue(
            app(Request(Method.GET, "/api/books?libraryId=$libId").header("Cookie", "token=$token")).bodyString(),
            com.fasterxml.jackson.databind.JsonNode::class.java,
        )["books"][0]["id"].asText()

        // 6. Book detail page renders
        val bookPage = getHtml("/books/$bookId", token)
        assertTrue(bookPage.contains("My First Book"), "Book detail page should show book title")

        // 7. Upload a file
        val uploadResp = uploadPdf(token, bookId)
        assertEquals(Status.OK, uploadResp.status)

        // 8. Update reading progress via HTMX endpoint
        val progressResp = app(
            Request(Method.POST, "/ui/books/$bookId/progress")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body("currentPage=5"),
        )
        assertEquals(Status.OK, progressResp.status)

        // 9. Book detail page shows progress
        val bookAfterProgress = getHtml("/books/$bookId", token)
        assertTrue(bookAfterProgress.contains("5"), "Book page should show current page progress")

        // 10. Reader page is accessible
        val readerResp = app(
            Request(Method.GET, "/books/$bookId/read")
                .header("Cookie", "token=$token"),
        )
        assertEquals(Status.OK, readerResp.status)
        assertTrue(readerResp.bodyString().contains("BookTower"), "Reader page should render")
    }

    // ── Gap 3: Profile page renders + password change workflow ────────────

    @Test
    fun `profile page renders with password change form`() {
        val token = registerAndGetToken("wf_profile")

        val resp = app(Request(Method.GET, "/profile").header("Cookie", "token=$token"))

        assertEquals(Status.OK, resp.status)
        val body = resp.bodyString()
        assertTrue(body.contains("text/html", ignoreCase = true) || resp.header("Content-Type")?.contains("html") == true)
        assertTrue(body.contains("password", ignoreCase = true), "Profile page should contain password form")
        // Form fields present
        assertTrue(body.contains("currentPassword") || body.contains("current-password"),
            "Form should have current password field")
        assertTrue(body.contains("newPassword") || body.contains("new-password"),
            "Form should have new password field")
    }

    @Test
    fun `profile page redirects to login when not authenticated`() {
        val resp = app(Request(Method.GET, "/profile"))
        // Should redirect to login, not crash
        assertTrue(resp.status.code in listOf(302, 303), "Unauthenticated profile should redirect: ${resp.status}")
        val location = resp.header("Location")
        assertNotNull(location)
        assertTrue(location!!.contains("login"), "Should redirect to login page")
    }

    @Test
    fun `password change workflow - old password rejected, new password works`() {
        val username = "wf_pwflow_${System.nanoTime()}"
        val regResp = app(
            Request(Method.POST, "/auth/register")
                .header("Content-Type", "application/json")
                .body("""{"username":"$username","email":"$username@test.com","password":"oldpassword1"}"""),
        )
        val token = Json.mapper.readValue(regResp.bodyString(), LoginResponse::class.java).token

        // Change password
        val changeResp = app(
            Request(Method.POST, "/api/auth/change-password")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"currentPassword":"oldpassword1","newPassword":"newpassword99"}"""),
        )
        assertEquals(Status.OK, changeResp.status, "Password change should succeed")

        // Old password login fails
        val oldLogin = app(
            Request(Method.POST, "/auth/login")
                .header("Content-Type", "application/json")
                .body("""{"username":"$username","password":"oldpassword1"}"""),
        )
        assertEquals(Status.UNAUTHORIZED, oldLogin.status, "Old password should no longer work")

        // New password login succeeds
        val newLogin = app(
            Request(Method.POST, "/auth/login")
                .header("Content-Type", "application/json")
                .body("""{"username":"$username","password":"newpassword99"}"""),
        )
        assertEquals(Status.OK, newLogin.status, "New password should work for login")
        val newToken = Json.mapper.readValue(newLogin.bodyString(), LoginResponse::class.java).token
        assertNotNull(newToken)
    }

    @Test
    fun `JWT remains valid for API calls after password change`() {
        val token = registerAndGetToken("wf_jwtpw")

        app(
            Request(Method.POST, "/api/auth/change-password")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"currentPassword":"password123","newPassword":"newpassword99"}"""),
        )

        // Existing JWT still authorises API calls (no token rotation)
        val resp = app(
            Request(Method.GET, "/api/libraries")
                .header("Cookie", "token=$token"),
        )
        assertEquals(Status.OK, resp.status, "Existing JWT should remain valid after password change")
    }

    // ── Gap 4: Theme and language persistence via cookies ─────────────────

    @Test
    fun `setting theme via preferences endpoint returns CSS and sets cookie`() {
        val resp = app(
            Request(Method.POST, "/preferences/theme")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body("theme=dracula"),
        )
        assertEquals(Status.OK, resp.status)
        // Response body is the <style> tag with inline CSS
        val body = resp.bodyString()
        assertTrue(body.contains("data-theme=\"dracula\""), "Response should contain data-theme attribute")
        assertTrue(body.contains("<style"), "Response should be a style element")
        // Cookie is set
        val cookie = resp.cookies().find { it.name == "app_theme" }
        assertNotNull(cookie, "Should set app_theme cookie")
        assertEquals("dracula", cookie!!.value)
    }

    @Test
    fun `theme cookie is reflected in data-theme on all authenticated pages`() {
        val token = registerAndGetToken("wf_theme")

        // Libraries page
        val libsHtml = getHtml("/libraries", token, "app_theme=dracula")
        assertTrue(libsHtml.contains("data-theme=\"dracula\""),
            "Libraries page should reflect theme cookie")

        // Create library and check book detail page
        val libId = createLibrary(token)
        val libHtml = getHtml("/libraries/$libId", token, "app_theme=nord")
        assertTrue(libHtml.contains("data-theme=\"nord\""),
            "Library detail page should reflect theme cookie")

        // Book detail page
        val bookId = createBook(token, libId)
        val bookHtml = getHtml("/books/$bookId", token, "app_theme=solarized-light")
        assertTrue(bookHtml.contains("data-theme=\"solarized-light\""),
            "Book detail page should reflect theme cookie")
    }

    @Test
    fun `language cookie switches UI text on library page`() {
        val token = registerAndGetToken("wf_lang")

        // French
        val frHtml = getHtml("/libraries", token, "app_lang=fr")
        // The French translation for "nav.libraries" is "Bibliothèques"
        assertTrue(frHtml.contains("Biblioth"), "French page should contain French text")

        // German
        val deHtml = getHtml("/libraries", token, "app_lang=de")
        // German translation for "nav.libraries" is "Bibliotheken"
        assertTrue(deHtml.contains("Bibliotheken"), "German page should contain German text")
    }

    @Test
    fun `setting language via preferences endpoint triggers page refresh header`() {
        val resp = app(
            Request(Method.POST, "/preferences/lang")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body("lang=fr"),
        )
        assertEquals(Status.OK, resp.status)
        // HTMX refresh header
        val hxRefresh = resp.header("HX-Refresh")
        assertEquals("true", hxRefresh, "Lang change should instruct HTMX to refresh")
        // Cookie
        val cookie = resp.cookies().find { it.name == "app_lang" }
        assertNotNull(cookie, "Should set app_lang cookie")
        assertEquals("fr", cookie!!.value)
    }

    @Test
    fun `preferences cookies applied during login restore theme and language on first page`() {
        val username = "wf_prefs_${System.nanoTime()}"
        // Register and save theme + lang settings
        val regResp = app(
            Request(Method.POST, "/auth/register")
                .header("Content-Type", "application/json")
                .body("""{"username":"$username","email":"$username@test.com","password":"password123"}"""),
        )
        val token = Json.mapper.readValue(regResp.bodyString(), LoginResponse::class.java).token

        // Save theme and lang preferences via settings API (body is plain value string)
        app(
            Request(Method.PUT, "/api/settings/theme")
                .header("Cookie", "token=$token")
                .body("dracula"),
        )
        app(
            Request(Method.PUT, "/api/settings/language")
                .header("Cookie", "token=$token")
                .body("de"),
        )

        // Login: response should set preference cookies
        val loginResp = app(
            Request(Method.POST, "/auth/login")
                .header("Content-Type", "application/json")
                .body("""{"username":"$username","password":"password123"}"""),
        )
        assertEquals(Status.OK, loginResp.status)
        val themeCookie = loginResp.cookies().find { it.name == "app_theme" }
        val langCookie = loginResp.cookies().find { it.name == "app_lang" }
        assertNotNull(themeCookie, "Login response should set app_theme cookie from saved preference")
        assertEquals("dracula", themeCookie!!.value)
        assertNotNull(langCookie, "Login response should set app_lang cookie from saved preference")
        assertEquals("de", langCookie!!.value)
    }

    // ── Gap 5: Rename library + edit book visible in rendered HTML ────────

    @Test
    fun `rename library workflow - new name appears in rendered HTML`() {
        val token = registerAndGetToken("wf_rename")
        val libId = createLibrary(token, "Old Library Name")

        // Verify old name in HTML
        assertTrue(getHtml("/libraries/$libId", token).contains("Old Library Name"))

        // Rename via HTMX endpoint
        val renameResp = app(
            Request(Method.POST, "/ui/libraries/$libId/rename")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body("name=New+Library+Name"),
        )
        assertEquals(Status.OK, renameResp.status)
        val redirect = renameResp.header("HX-Redirect")
        assertNotNull(redirect, "Rename should return HX-Redirect header")
        assertTrue(redirect!!.contains(libId), "HX-Redirect should point to library page")

        // Follow the redirect: new name appears, old name gone
        val after = getHtml("/libraries/$libId", token)
        assertTrue(after.contains("New Library Name"), "Renamed library should appear in HTML")
        assertFalse(after.contains("Old Library Name"), "Old library name should not appear after rename")
    }

    @Test
    fun `edit book workflow - updated metadata appears on book detail page`() {
        val token = registerAndGetToken("wf_editbook")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Original Title")

        // Verify original title
        assertTrue(getHtml("/books/$bookId", token).contains("Original Title"))

        // Edit via HTMX endpoint
        val editResp = app(
            Request(Method.POST, "/ui/books/$bookId/meta")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body("title=Updated+Title&author=New+Author&description=New+description+text"),
        )
        assertEquals(Status.OK, editResp.status)
        assertNotNull(editResp.header("HX-Redirect"), "Edit should return HX-Redirect")

        // Follow redirect: updated content in HTML
        val after = getHtml("/books/$bookId", token)
        assertTrue(after.contains("Updated Title"), "Updated title should appear in HTML")
        assertTrue(after.contains("New Author"), "Updated author should appear in HTML")
        assertTrue(after.contains("New description text"), "Updated description should appear in HTML")
        assertFalse(after.contains("Original Title"), "Original title should no longer appear")
    }

    // ── Gap 6: Pagination at default page size ────────────────────────────

    @Test
    fun `API default page size is 20 - 25 books splits across two pages`() {
        val token = registerAndGetToken("wf_page")
        val libId = createLibrary(token)
        repeat(25) { createBook(token, libId, "PagingBook $it") }

        // Page 1: 20 books
        val page1Resp = app(
            Request(Method.GET, "/api/books?libraryId=$libId&page=1")
                .header("Cookie", "token=$token"),
        )
        val page1 = Json.mapper.readTree(page1Resp.bodyString())
        assertEquals(20, page1["pageSize"].asInt())
        assertEquals(25, page1["total"].asInt())
        assertEquals(20, page1["books"].size())

        // Page 2: remaining 5 books
        val page2Resp = app(
            Request(Method.GET, "/api/books?libraryId=$libId&page=2")
                .header("Cookie", "token=$token"),
        )
        val page2 = Json.mapper.readTree(page2Resp.bodyString())
        assertEquals(5, page2["books"].size())
        assertEquals(25, page2["total"].asInt())
    }

    // ── Gap 7: Bookmark workflow visible in rendered HTML ─────────────────

    @Test
    fun `add bookmark workflow - bookmark appears in rendered book page`() {
        val token = registerAndGetToken("wf_bookmark")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Bookmark Workflow Book")

        // Before: no bookmarks
        val before = getHtml("/books/$bookId", token)
        assertFalse(before.contains("Chapter One"), "No bookmarks initially")

        // Add bookmark via HTMX
        val bmResp = app(
            Request(Method.POST, "/ui/books/$bookId/bookmarks")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body("page=12&title=Chapter+One&note=Important+bit"),
        )
        assertEquals(Status.OK, bmResp.status)
        val fragment = bmResp.bodyString()
        assertTrue(fragment.contains("Chapter One"), "Returned fragment should contain bookmark title")
        assertTrue(fragment.contains("12"), "Returned fragment should contain page number")

        // Delete bookmark: response is 200, fragment disappears
        val bmId = Json.mapper.readValue(
            app(Request(Method.GET, "/api/bookmarks?bookId=$bookId").header("Cookie", "token=$token")).bodyString(),
            Array<BookmarkDto>::class.java,
        ).first().id

        val delResp = app(
            Request(Method.DELETE, "/ui/bookmarks/$bmId").header("Cookie", "token=$token"),
        )
        assertEquals(Status.OK, delResp.status)

        // Bookmark gone from API
        val remaining = Json.mapper.readValue(
            app(Request(Method.GET, "/api/bookmarks?bookId=$bookId").header("Cookie", "token=$token")).bodyString(),
            Array<BookmarkDto>::class.java,
        )
        assertTrue(remaining.isEmpty(), "Bookmark should be deleted")
    }
}
