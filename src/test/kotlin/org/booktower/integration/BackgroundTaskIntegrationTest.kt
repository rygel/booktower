package org.booktower.integration

import org.booktower.TestFixture
import org.booktower.config.Json
import org.booktower.services.AdminService
import org.booktower.services.AnalyticsService
import org.booktower.services.AnnotationService
import org.booktower.services.ApiTokenService
import org.booktower.services.AuthService
import org.booktower.services.BackgroundTaskService
import org.booktower.services.BookService
import org.booktower.services.BookmarkService
import org.booktower.services.ComicService
import org.booktower.services.EpubMetadataService
import org.booktower.services.ExportService
import org.booktower.services.GoodreadsImportService
import org.booktower.services.JwtService
import org.booktower.services.LibraryService
import org.booktower.services.MagicShelfService
import org.booktower.services.PasswordResetService
import org.booktower.services.PdfMetadataService
import org.booktower.services.ReadingSessionService
import org.booktower.services.SeedService
import org.booktower.services.UserSettingsService
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BackgroundTaskIntegrationTest {
    private val config = TestFixture.config
    private val jdbi = TestFixture.database.getJdbi()
    private val taskService = BackgroundTaskService()

    private fun buildApp(): org.http4k.core.HttpHandler {
        val jwtService = JwtService(config.security)
        val authService = AuthService(jdbi, jwtService)
        val pdfMetadataService = PdfMetadataService(jdbi, config.storage.coversPath)
        val libraryService = LibraryService(jdbi, pdfMetadataService)
        val bookmarkService = BookmarkService(jdbi)
        val userSettingsService = UserSettingsService(jdbi)
        val analyticsService = AnalyticsService(jdbi, userSettingsService)
        val readingSessionService = ReadingSessionService(jdbi)
        val bookService = BookService(jdbi, analyticsService, readingSessionService)
        val adminService = AdminService(jdbi)
        val annotationService = AnnotationService(jdbi)
        val magicShelfService = MagicShelfService(jdbi, bookService)
        val passwordResetService = PasswordResetService(jdbi)
        val apiTokenService = ApiTokenService(jdbi)
        val exportService = ExportService(jdbi)
        val epubMetadataService = EpubMetadataService(jdbi, config.storage.coversPath)
        val comicService = ComicService()
        val goodreadsImportService = GoodreadsImportService(bookService)
        val seedService = SeedService(bookService, libraryService, config.storage.coversPath, config.storage.booksPath)
        return buildTestApp(
            authService = authService,
            libraryService = libraryService,
            bookService = bookService,
            jwtService = jwtService,
            backgroundTaskService = taskService,
        )
    }

    @Test
    fun `GET api tasks returns empty list for new user`() {
        val app = buildApp()
        val token = registerToken(app)
        val resp = app(Request(Method.GET, "/api/tasks").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        val tasks = Json.mapper.readTree(resp.bodyString())
        assertTrue(tasks.isArray)
        assertEquals(0, tasks.size())
    }

    @Test
    fun `task recorded via service appears in GET api tasks`() {
        val app = buildApp()
        val token = registerToken(app)
        val userId = extractUserId(app, token)

        val taskId = taskService.start(userId, "test.job", "Test job label")
        taskService.complete(taskId, "finished OK")

        val resp = app(Request(Method.GET, "/api/tasks").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        val tasks = Json.mapper.readTree(resp.bodyString())
        val task = tasks.firstOrNull { it.get("id").asText() == taskId }
        assertNotNull(task, "Expected task $taskId in list")
        assertEquals("DONE", task!!.get("status").asText())
        assertEquals("finished OK", task.get("detail").asText())
    }

    @Test
    fun `DELETE api tasks id dismisses completed task`() {
        val app = buildApp()
        val token = registerToken(app)
        val userId = extractUserId(app, token)

        val taskId = taskService.start(userId, "test.job", "Dismissable")
        taskService.complete(taskId)

        val resp = app(Request(Method.DELETE, "/api/tasks/$taskId").header("Cookie", "token=$token"))
        assertEquals(Status.NO_CONTENT, resp.status)

        val listResp = app(Request(Method.GET, "/api/tasks").header("Cookie", "token=$token"))
        val tasks = Json.mapper.readTree(listResp.bodyString())
        assertTrue(tasks.none { it.get("id").asText() == taskId }, "Dismissed task should not appear")
    }

    @Test
    fun `DELETE api tasks id returns 404 for running task`() {
        val app = buildApp()
        val token = registerToken(app)
        val userId = extractUserId(app, token)

        val taskId = taskService.start(userId, "test.job", "Still running")
        // Not completed — still RUNNING

        val resp = app(Request(Method.DELETE, "/api/tasks/$taskId").header("Cookie", "token=$token"))
        assertEquals(Status.NOT_FOUND, resp.status)
    }

    @Test
    fun `GET api admin tasks requires admin`() {
        val app = buildApp()
        val token = registerToken(app)
        val resp = app(Request(Method.GET, "/api/admin/tasks").header("Cookie", "token=$token"))
        assertEquals(Status.FORBIDDEN, resp.status)
    }

    @Test
    fun `BackgroundTaskService evicts old tasks when over capacity`() {
        val svc = BackgroundTaskService(maxTasks = 5)
        val userId = UUID.randomUUID()
        repeat(7) {
            val id = svc.start(userId, "type", "label $it")
            svc.complete(id)
        }
        assertTrue(svc.listAll().size <= 5, "Should evict down to capacity")
    }

    @Test
    fun `BackgroundTaskService user isolation - user only sees own tasks`() {
        val userId1 = UUID.randomUUID()
        val userId2 = UUID.randomUUID()
        val id1 = taskService.start(userId1, "job", "user1 job")
        val id2 = taskService.start(userId2, "job", "user2 job")

        val user1Tasks = taskService.listForUser(userId1)
        assertTrue(user1Tasks.any { it.id == id1 })
        assertTrue(user1Tasks.none { it.id == id2 })
    }

    private fun registerToken(app: org.http4k.core.HttpHandler): String {
        val name = "bgtask_${System.nanoTime()}"
        val resp =
            app(
                Request(Method.POST, "/auth/register")
                    .header("Content-Type", "application/json")
                    .body("""{"username":"$name","email":"$name@test.com","password":"password123"}"""),
            )
        return Json.mapper
            .readTree(resp.bodyString())
            .get("token")
            .asText()
    }

    private fun extractUserId(
        app: org.http4k.core.HttpHandler,
        token: String,
    ): UUID {
        // Decode from JWT payload (base64 middle section)
        val payload = token.split(".").getOrNull(1) ?: error("Invalid token")
        val decoded =
            String(
                java.util.Base64
                    .getUrlDecoder()
                    .decode(padding(payload)),
            )
        val sub =
            Json.mapper
                .readTree(decoded)
                .get("sub")
                .asText()
        return UUID.fromString(sub)
    }

    private fun padding(s: String): String {
        val rem = s.length % 4
        return if (rem == 0) s else s + "=".repeat(4 - rem)
    }
}
