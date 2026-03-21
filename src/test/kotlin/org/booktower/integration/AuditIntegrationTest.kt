package org.booktower.integration

import org.booktower.TestFixture
import org.booktower.config.Json
import org.booktower.services.AdminService
import org.booktower.services.AnalyticsService
import org.booktower.services.AnnotationService
import org.booktower.services.ApiTokenService
import org.booktower.services.AuditService
import org.booktower.services.AuthService
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
                .body("""{"username":"$username","email":"$username@test.com","password":"password123"}"""),
        )
        // Login
        app(
            Request(Method.POST, "/auth/login")
                .header("Content-Type", "application/json")
                .body("""{"username":"$username","password":"password123"}"""),
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
                .body("""{"username":"$username","email":"$username@test.com","password":"password123"}"""),
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
                    .body("""{"username":"$username","email":"$username@test.com","password":"password123"}"""),
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
        val result = authService.register(org.booktower.models.CreateUserRequest(username, "$username@test.com", "password123"))
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
