package org.runary.browser

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import kotlin.test.assertTrue

/**
 * Browser-level tests for the book detail page, profile page, and reader page.
 *
 * Run with: mvn test -P browser-tests -Dtest="PageBrowserTest"
 */
@Tag("browser")
class PageBrowserTest : BrowserTestBase() {
    // ── Book detail page tests ───────────────────────────────────────────────────

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    fun `book detail page renders title and author`() {
        val (page, token) = newAuthenticatedPage("bdt1")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Detail Title Test")

        // Set author via the API
        app(
            org.http4k.core
                .Request(org.http4k.core.Method.POST, "/ui/books/$bookId/meta")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body("title=Detail+Title+Test&author=Jane+Doe"),
        )

        page.navigate("$baseUrl/books/$bookId")
        page.waitForTimeout(1000.0)

        val h1 = page.querySelector("h1.book-title")
        assertTrue(h1 != null, "Book detail page should have an h1 with class book-title")
        val titleText = h1.textContent() ?: ""
        assertTrue(titleText.contains("Detail Title Test"), "h1 should contain the book title, got: '$titleText'")

        val authorLink = page.querySelector("a.book-author-link")
        assertTrue(authorLink != null, "Book detail page should have an author link")
        val authorText = authorLink.textContent() ?: ""
        assertTrue(authorText.contains("Jane Doe"), "Author link should contain author name, got: '$authorText'")

        page.close()
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    fun `book detail page shows read button for uploaded book`() {
        val (page, token) = newAuthenticatedPage("bdt2")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Read Button Test")
        uploadFile(token, bookId, "book.epub", minimalEpubBytes())

        page.navigate("$baseUrl/books/$bookId")
        page.waitForTimeout(1000.0)

        val readBtn = page.querySelector("a.btn-primary[href*='/read']")
        assertTrue(readBtn != null, "Book detail page should have a read button when a file is uploaded")

        page.close()
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    fun `book detail page shows status selector`() {
        val (page, token) = newAuthenticatedPage("bdt3")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Status Selector Test")

        page.navigate("$baseUrl/books/$bookId")
        page.waitForTimeout(1000.0)

        val statusSelect = page.querySelector("#book-status-select")
        assertTrue(statusSelect != null, "Book detail page should have a status select dropdown")

        page.close()
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    fun `book detail page shows star rating`() {
        val (page, token) = newAuthenticatedPage("bdt4")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Star Rating Test")

        page.navigate("$baseUrl/books/$bookId")
        page.waitForTimeout(1000.0)

        val starRating = page.querySelector("#star-rating")
        assertTrue(starRating != null, "Book detail page should have a star-rating container")

        val starButtons = page.querySelectorAll(".star-btn")
        assertTrue(starButtons.size == 5, "Book detail page should have 5 star buttons, got: ${starButtons.size}")

        page.close()
    }

    // ── Profile page tests ───────────────────────────────────────────────────────

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    fun `profile page renders account info`() {
        val (page, token) = newAuthenticatedPage("bdt5")

        page.navigate("$baseUrl/profile")
        page.waitForTimeout(1000.0)

        // The profile page has a definition list with username and email
        val dlElement = page.querySelector("dl.profile-dl")
        assertTrue(dlElement != null, "Profile page should have a profile-dl definition list")

        val dlText = dlElement.textContent() ?: ""
        assertTrue(dlText.contains("bdt5"), "Profile dl should contain the username prefix 'bdt5', got: '$dlText'")
        assertTrue(dlText.contains("@test.com"), "Profile dl should contain the email domain, got: '$dlText'")

        page.close()
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    fun `profile page has theme selector`() {
        val (page, token) = newAuthenticatedPage("bdt6")

        page.navigate("$baseUrl/profile")
        page.waitForTimeout(1000.0)

        val themeSelect = page.querySelector("select[name='theme']")
        assertTrue(themeSelect != null, "Profile page should have a theme select dropdown")

        page.close()
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    fun `profile page has language selector`() {
        val (page, token) = newAuthenticatedPage("bdt7")

        page.navigate("$baseUrl/profile")
        page.waitForTimeout(1000.0)

        val langSelect = page.querySelector("select[name='lang']")
        assertTrue(langSelect != null, "Profile page should have a language select dropdown")

        page.close()
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    fun `profile page has change password form`() {
        val (page, token) = newAuthenticatedPage("bdt8")

        page.navigate("$baseUrl/profile")
        page.waitForTimeout(1000.0)

        val pwForm = page.querySelector("#pw-form")
        assertTrue(pwForm != null, "Profile page should have a password change form")

        val currentPw = page.querySelector("#current-password")
        assertTrue(currentPw != null, "Profile page should have current password field")

        val newPw = page.querySelector("#new-password")
        assertTrue(newPw != null, "Profile page should have new password field")

        val confirmPw = page.querySelector("#confirm-password")
        assertTrue(confirmPw != null, "Profile page should have confirm password field")

        page.close()
    }

    // ── Reader page tests ────────────────────────────────────────────────────────

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    fun `reader page loads without 500 error`() {
        val (page, token) = newAuthenticatedPage("bdt9")
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Reader Load Test")
        uploadFile(token, bookId, "book.epub", minimalEpubBytes())

        val response = page.navigate("$baseUrl/books/$bookId/read")
        val status = response?.status() ?: -1

        assertTrue(status != 500, "Reader page should not return a 500 error, got status: $status")
        assertTrue(status in 200..399, "Reader page should return a success status, got: $status")

        page.close()
    }
}
