package org.booktower.integration

import com.fasterxml.jackson.module.kotlin.readValue
import org.booktower.TestFixture
import org.booktower.config.Json
import org.booktower.models.BookListDto
import org.booktower.models.LibraryDto
import org.booktower.models.LoginResponse
import org.booktower.services.BackgroundTask
import org.booktower.services.TaskStatus
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * On-demand tests that hit real upstream sources (Gutenberg, LibriVox, archive.org).
 * Excluded from regular CI. Run manually to verify upstream integrations still work:
 *
 *   mvn test -P e2e-network-tests
 *
 * These tests are slow (network downloads) and depend on external service availability.
 */
@Tag("e2e-network")
class UpstreamDownloadE2ETest : IntegrationTestBase() {
    private fun registerAdminAndGetToken(prefix: String = "upstream"): String {
        val username = "${prefix}_${System.nanoTime()}"
        val registerResponse =
            app(
                Request(Method.POST, "/auth/register")
                    .header("Content-Type", "application/json")
                    .body("""{"username":"$username","email":"$username@test.com","password":"password123"}"""),
            )
        assertEquals(Status.CREATED, registerResponse.status)
        val userId =
            Json.mapper
                .readValue(registerResponse.bodyString(), LoginResponse::class.java)
                .user.id

        TestFixture.database.getJdbi().useHandle<Exception> { handle ->
            handle
                .createUpdate("UPDATE users SET is_admin = true WHERE id = ?")
                .bind(0, userId)
                .execute()
        }

        val loginResponse =
            app(
                Request(Method.POST, "/auth/login")
                    .header("Content-Type", "application/json")
                    .body("""{"username":"$username","password":"password123"}"""),
            )
        assertEquals(Status.OK, loginResponse.status)
        return Json.mapper.readValue(loginResponse.bodyString(), LoginResponse::class.java).token
    }

    /**
     * Polls /api/tasks until tasks of [expectedTaskType] are no longer RUNNING.
     * Waits [minWaitMs] before first check to let background threads register tasks.
     */
    private fun waitForTasksComplete(
        token: String,
        expectedTaskType: String,
        timeoutMs: Long = 120_000,
        minWaitMs: Long = 3_000,
    ) {
        Thread.sleep(minWaitMs)
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val tasks = getTasks(token)
            val relevant = tasks.filter { it.type == expectedTaskType }
            if (relevant.isNotEmpty() && relevant.none { it.status == TaskStatus.RUNNING }) {
                return
            }
            Thread.sleep(2_000)
        }
        fail("Timed out waiting for $expectedTaskType tasks to complete (${timeoutMs}ms)")
    }

    private fun getTasks(token: String): List<BackgroundTask> {
        val response =
            app(
                Request(Method.GET, "/api/tasks")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.OK, response.status)
        return Json.mapper.readValue(response.bodyString())
    }

    // ── Gutenberg EPUB downloads ──────────────────────────────────────────

    @Test
    @Timeout(180)
    fun `Gutenberg EPUB downloads succeed and files are servable`() {
        val token = registerAdminAndGetToken("gutenberg")

        // Seed libraries + books
        assertEquals(
            Status.OK,
            app(Request(Method.POST, "/admin/seed").header("Cookie", "token=$token")).status,
        )

        // Trigger file downloads
        assertEquals(
            Status.OK,
            app(Request(Method.POST, "/admin/seed/files").header("Cookie", "token=$token")).status,
        )

        // Wait for downloads
        waitForTasksComplete(token, "download-epub")

        // Check results
        val tasks = getTasks(token).filter { it.type == "download-epub" }
        assertTrue(tasks.isNotEmpty(), "Should have EPUB download tasks")
        val succeeded = tasks.count { it.status == TaskStatus.DONE }
        val failed = tasks.filter { it.status == TaskStatus.FAILED }
        assertTrue(
            succeeded > 0,
            "At least one EPUB should download successfully. Failed: ${failed.map { "${it.label}: ${it.detail}" }}",
        )

        // Verify a downloaded book is servable
        val books =
            Json.mapper
                .readValue(
                    app(Request(Method.GET, "/api/books?pageSize=100").header("Cookie", "token=$token")).bodyString(),
                    BookListDto::class.java,
                ).getBooks()
        val bookWithFile = books.firstOrNull { it.fileSize > 0 }
        if (bookWithFile != null) {
            val dl = app(Request(Method.GET, "/api/books/${bookWithFile.id}/file").header("Cookie", "token=$token"))
            assertEquals(Status.OK, dl.status, "Downloaded EPUB should be servable")
            assertTrue(
                dl.body.stream
                    .readBytes()
                    .size > 1000,
                "EPUB should have substantial content",
            )
        }
    }

    // ── LibriVox audiobook downloads ──────────────────────────────────────

    @Test
    @Timeout(180)
    fun `LibriVox audiobook downloads succeed`() {
        val token = registerAdminAndGetToken("librivox")

        assertEquals(
            Status.OK,
            app(Request(Method.POST, "/admin/seed/librivox").header("Cookie", "token=$token")).status,
        )

        // Verify library created
        val libraries =
            Json.mapper.readValue(
                app(Request(Method.GET, "/api/libraries").header("Cookie", "token=$token")).bodyString(),
                Array<LibraryDto>::class.java,
            )
        assertTrue(libraries.any { it.name.contains("LibriVox", ignoreCase = true) })

        // Wait for downloads
        waitForTasksComplete(token, "download-audiobook")

        val tasks = getTasks(token).filter { it.type == "download-audiobook" }
        assertTrue(tasks.isNotEmpty(), "Should have audiobook download tasks")
        val succeeded = tasks.count { it.status == TaskStatus.DONE }
        val failed = tasks.filter { it.status == TaskStatus.FAILED }
        assertTrue(
            succeeded > 0,
            "At least one audiobook should download. Failed: ${failed.map { "${it.label}: ${it.detail}" }}",
        )
    }

    // ── Archive.org comic downloads ───────────────────────────────────────

    @Test
    @Timeout(180)
    fun `Archive org comic downloads succeed and pages are servable`() {
        val token = registerAdminAndGetToken("comics")

        assertEquals(
            Status.OK,
            app(Request(Method.POST, "/admin/seed/comics").header("Cookie", "token=$token")).status,
        )

        // Verify library created
        val libraries =
            Json.mapper.readValue(
                app(Request(Method.GET, "/api/libraries").header("Cookie", "token=$token")).bodyString(),
                Array<LibraryDto>::class.java,
            )
        assertTrue(libraries.any { it.name.contains("Comics", ignoreCase = true) })

        // Wait for downloads
        waitForTasksComplete(token, "download-comic")

        val tasks = getTasks(token).filter { it.type == "download-comic" }
        assertTrue(tasks.isNotEmpty(), "Should have comic download tasks")
        val succeeded = tasks.count { it.status == TaskStatus.DONE }
        val failed = tasks.filter { it.status == TaskStatus.FAILED }
        assertTrue(
            succeeded > 0,
            "At least one comic should download. Failed: ${failed.map { "${it.label}: ${it.detail}" }}",
        )

        // Verify a downloaded comic's pages are servable
        val books =
            Json.mapper
                .readValue(
                    app(Request(Method.GET, "/api/books?pageSize=50").header("Cookie", "token=$token")).bodyString(),
                    BookListDto::class.java,
                ).getBooks()
        val comicWithFile = books.firstOrNull { it.fileSize > 0 }
        if (comicWithFile != null) {
            val pagesResp =
                app(
                    Request(Method.GET, "/api/books/${comicWithFile.id}/comic/pages").header("Cookie", "token=$token"),
                )
            assertEquals(Status.OK, pagesResp.status)
            assertTrue(pagesResp.bodyString().contains("pageCount"), "Should report page count")

            val pageResp =
                app(
                    Request(Method.GET, "/api/books/${comicWithFile.id}/comic/0").header("Cookie", "token=$token"),
                )
            assertEquals(Status.OK, pageResp.status, "Should be able to serve first comic page")
            assertTrue(
                pageResp.header("Content-Type")?.contains("image") == true,
                "Comic page should be served as image",
            )
        }
    }
}
