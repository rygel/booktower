package org.runary.integration

import org.runary.TestFixture
import org.runary.config.Json
import org.runary.services.AdminService
import org.runary.services.AnalyticsService
import org.runary.services.AnnotationService
import org.runary.services.ApiTokenService
import org.runary.services.AuthService
import org.runary.services.BookService
import org.runary.services.BookmarkService
import org.runary.services.ComicService
import org.runary.services.EpubMetadataService
import org.runary.services.ExportService
import org.runary.services.GoodreadsImportService
import org.runary.services.JwtService
import org.runary.services.LibraryHealthService
import org.runary.services.LibraryService
import org.runary.services.MagicShelfService
import org.runary.services.PasswordResetService
import org.runary.services.PdfMetadataService
import org.runary.services.ReadingSessionService
import org.runary.services.SeedService
import org.runary.services.UserSettingsService
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LibraryHealthIntegrationTest {
    private val config = TestFixture.config
    private val jdbi = TestFixture.database.getJdbi()
    private val healthService = LibraryHealthService(jdbi)

    private fun buildApp(): HttpHandler {
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
            libraryHealthService = healthService,
        )
    }

    private fun registerAndGetToken(
        app: HttpHandler,
        prefix: String = "hlth",
    ): String {
        val name = "${prefix}_${System.nanoTime()}"
        val resp =
            app(
                Request(Method.POST, "/auth/register")
                    .header("Content-Type", "application/json")
                    .body("""{"username":"$name","email":"$name@test.com","password":"${org.runary.TestPasswords.DEFAULT}"}"""),
            )
        return Json.mapper
            .readTree(resp.bodyString())
            .get("token")
            .asText()
    }

    // ── end-to-end tests ─────────────────────────────────────────────────────

    @Test
    fun `GET api libraries health returns empty report for user with no libraries`() {
        val app = buildApp()
        val token = registerAndGetToken(app)
        val resp = app(Request(Method.GET, "/api/libraries/health").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        val tree = Json.mapper.readTree(resp.bodyString())
        assertTrue(tree.get("libraries").isArray)
        assertEquals(0, tree.get("libraries").size())
        assertEquals(0, tree.get("totalIssues").asInt())
    }

    @Test
    fun `health check flags non-existent library path via direct service call`() {
        val jwtService = JwtService(config.security)
        val authService = AuthService(jdbi, jwtService)

        val username = "hlthpath_${System.nanoTime()}"
        val userResult = authService.register(org.runary.models.CreateUserRequest(username, "$username@t.com", org.runary.TestPasswords.DEFAULT))
        val userId = java.util.UUID.fromString(userResult.getOrThrow().user.id)

        // Raw SQL for library: LibraryService.createLibrary() auto-creates the directory,
        // but this test needs a library pointing to a non-existent path.
        val libId =
            java.util.UUID
                .randomUUID()
                .toString()
        jdbi.useHandle<Exception> { h ->
            h
                .createUpdate("INSERT INTO libraries (id,user_id,name,path,created_at) VALUES (?,?,?,?,?)")
                .bind(0, libId)
                .bind(1, userId.toString())
                .bind(2, "BadPath")
                .bind(3, "/this/path/absolutely/does/not/exist/${System.nanoTime()}")
                .bind(
                    4,
                    java.time.Instant
                        .now()
                        .toString(),
                ).execute()
        }

        val report = healthService.check(userId)
        assertEquals(1, report.libraries.size)
        assertFalse(report.libraries[0].pathAccessible, "Non-existent path should be inaccessible")
    }

    @Test
    fun `health check flags book with missing file`() {
        val app = buildApp()
        val token = registerAndGetToken(app)

        val libResp =
            app(
                Request(Method.POST, "/api/libraries")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"name":"HealthLib","path":"./data/test-hlth"}"""),
            )
        val libId =
            Json.mapper
                .readTree(libResp.bodyString())
                .get("id")
                .asText()

        // Create a book pointing at a file that doesn't exist
        app(
            Request(Method.POST, "/api/books")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"title":"Ghost Book","author":null,"description":null,"libraryId":"$libId"}"""),
        )

        val resp = app(Request(Method.GET, "/api/libraries/health").header("Cookie", "token=$token"))
        val libs = Json.mapper.readTree(resp.bodyString()).get("libraries")
        val report = libs.firstOrNull { it.get("libraryId").asText() == libId }
        assertNotNull(report)
        val issues = report!!.get("issues")
        assertTrue(
            issues.any { it.get("issue").asText() == "file_missing" },
            "Expected file_missing issue for book with no file",
        )
    }

    @Test
    fun `health check includes no_metadata issue for book without author or isbn`() {
        val app = buildApp()
        val token = registerAndGetToken(app)

        val libResp =
            app(
                Request(Method.POST, "/api/libraries")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"name":"MetaLib","path":"./data/test-meta"}"""),
            )
        val libId =
            Json.mapper
                .readTree(libResp.bodyString())
                .get("id")
                .asText()
        app(
            Request(Method.POST, "/api/books")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"title":"No Meta Book","author":null,"description":null,"libraryId":"$libId"}"""),
        )

        val resp = app(Request(Method.GET, "/api/libraries/health").header("Cookie", "token=$token"))
        val libs = Json.mapper.readTree(resp.bodyString()).get("libraries")
        val report = libs.firstOrNull { it.get("libraryId").asText() == libId }!!
        val issues = report.get("issues")
        assertTrue(
            issues.any { it.get("issue").asText() == "no_metadata" },
            "Expected no_metadata issue for book without author/isbn",
        )
    }

    @Test
    fun `health check requires authentication`() {
        val app = buildApp()
        val resp = app(Request(Method.GET, "/api/libraries/health"))
        assertEquals(Status.UNAUTHORIZED, resp.status)
    }

    @Test
    fun `LibraryHealthService check respects user boundaries`() {
        val jwtService = JwtService(config.security)
        val authService = AuthService(jdbi, jwtService)

        val uname1 = "hlthuser1_${System.nanoTime()}"
        val uname2 = "hlthuser2_${System.nanoTime()}"
        val userId1 =
            java.util.UUID.fromString(
                authService
                    .register(org.runary.models.CreateUserRequest(uname1, "$uname1@t.com", org.runary.TestPasswords.DEFAULT))
                    .getOrThrow()
                    .user.id,
            )
        val userId2 =
            java.util.UUID.fromString(
                authService
                    .register(org.runary.models.CreateUserRequest(uname2, "$uname2@t.com", org.runary.TestPasswords.DEFAULT))
                    .getOrThrow()
                    .user.id,
            )

        // Raw SQL for libraries: this test only checks user isolation, not path access.
        // Using raw SQL avoids creating real directories on disk.
        val now =
            java.time.Instant
                .now()
                .toString()
        jdbi.useHandle<Exception> { h ->
            for ((uid, libPath) in listOf(userId1 to "/tmp/boundary_${System.nanoTime()}_1", userId2 to "/tmp/boundary_${System.nanoTime()}_2")) {
                val libId =
                    java.util.UUID
                        .randomUUID()
                        .toString()
                h
                    .createUpdate("INSERT INTO libraries (id,user_id,name,path,created_at) VALUES (?,?,?,?,?)")
                    .bind(0, libId)
                    .bind(1, uid.toString())
                    .bind(2, "lib")
                    .bind(3, libPath)
                    .bind(4, now)
                    .execute()
            }
        }

        val report1 = healthService.check(userId1)
        val report2 = healthService.check(userId2)
        assertEquals(1, report1.libraries.size)
        assertEquals(1, report2.libraries.size)
        assertFalse(report1.libraries[0].libraryId == report2.libraries[0].libraryId)
    }
}
