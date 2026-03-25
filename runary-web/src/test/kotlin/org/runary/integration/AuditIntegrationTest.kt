package org.runary.integration

import org.runary.TestFixture
import org.runary.config.Json
import org.runary.services.AdminService
import org.runary.services.AnalyticsService
import org.runary.services.AnnotationService
import org.runary.services.ApiTokenService
import org.runary.services.AuditService
import org.runary.services.AuthService
import org.runary.services.BookService
import org.runary.services.BookmarkService
import org.runary.services.ComicService
import org.runary.services.EpubMetadataService
import org.runary.services.ExportService
import org.runary.services.GoodreadsImportService
import org.runary.services.JwtService
import org.runary.services.LibraryService
import org.runary.services.MagicShelfService
import org.runary.services.PasswordResetService
import org.runary.services.PdfMetadataService
import org.runary.services.ReadingSessionService
import org.runary.services.SeedService
import org.runary.services.UserSettingsService
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class AuditIntegrationTest {
    private val config = TestFixture.config
    private val jdbi = TestFixture.database.getJdbi()
    private val auditService = AuditService(jdbi)

    private fun buildAppWithAudit(): org.http4k.core.HttpHandler {
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
            auditService = auditService,
        )
    }

    @Test
    fun `login emits audit entry`() {
        val app = buildAppWithAudit()
        val username = "auditlogin_${System.nanoTime()}"
        // Register first
        app(
            Request(Method.POST, "/auth/register")
                .header("Content-Type", "application/json")
                .body("""{"username":"$username","email":"$username@test.com","password":"${org.runary.TestPasswords.DEFAULT}"}"""),
        )
        // Login
        app(
            Request(Method.POST, "/auth/login")
                .header("Content-Type", "application/json")
                .body("""{"username":"$username","password":"${org.runary.TestPasswords.DEFAULT}"}"""),
        )

        val entries = auditService.listRecent(50)
        assertTrue(
            entries.any { it.action == "user.login" && it.actorName == username },
            "Expected user.login audit entry for $username",
        )
    }

    @Test
    fun `register emits audit entry`() {
        val app = buildAppWithAudit()
        val username = "auditreg_${System.nanoTime()}"
        app(
            Request(Method.POST, "/auth/register")
                .header("Content-Type", "application/json")
                .body("""{"username":"$username","email":"$username@test.com","password":"${org.runary.TestPasswords.DEFAULT}"}"""),
        )

        val entries = auditService.listRecent(50)
        assertTrue(
            entries.any { it.action == "user.register" && it.actorName == username },
            "Expected user.register audit entry for $username",
        )
    }

    @Test
    fun `GET api admin audit requires admin token`() {
        val app = buildAppWithAudit()
        val username = "auditauth_${System.nanoTime()}"
        val resp =
            app(
                Request(Method.POST, "/auth/register")
                    .header("Content-Type", "application/json")
                    .body("""{"username":"$username","email":"$username@test.com","password":"${org.runary.TestPasswords.DEFAULT}"}"""),
            )
        val token =
            Json.mapper
                .readTree(resp.bodyString())
                .get("token")
                .asText()
        val auditResp =
            app(
                Request(Method.GET, "/api/admin/audit").header("Cookie", "token=$token"),
            )
        assertEquals(Status.FORBIDDEN, auditResp.status)
    }

    @Test
    fun `AuditService listRecent returns entries in descending order`() {
        val jwtService = JwtService(config.security)
        val authService = AuthService(jdbi, jwtService)
        val username = "auditorder_${System.nanoTime()}"
        val result = authService.register(org.runary.models.CreateUserRequest(username, "$username@test.com", org.runary.TestPasswords.DEFAULT))
        val userIdStr = result.getOrThrow().user.id
        val userId = java.util.UUID.fromString(userIdStr)
        auditService.record(userId, "testuser", "test.action.1")
        Thread.sleep(10)
        auditService.record(userId, "testuser", "test.action.2")

        val entries = auditService.listForUser(userId)
        assertTrue(entries.size >= 2)
        // Most recent first
        val idx1 = entries.indexOfFirst { it.action == "test.action.1" }
        val idx2 = entries.indexOfFirst { it.action == "test.action.2" }
        assertTrue(idx2 < idx1, "test.action.2 (newer) should appear before test.action.1 (older)")
    }
}
