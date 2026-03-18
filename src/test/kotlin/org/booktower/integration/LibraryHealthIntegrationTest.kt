package org.booktower.integration

import org.booktower.TestFixture
import org.booktower.config.Json
import org.booktower.services.AdminService
import org.booktower.services.AnalyticsService
import org.booktower.services.AnnotationService
import org.booktower.services.ApiTokenService
import org.booktower.services.AuthService
import org.booktower.services.BookService
import org.booktower.services.BookmarkService
import org.booktower.services.ComicService
import org.booktower.services.EpubMetadataService
import org.booktower.services.ExportService
import org.booktower.services.GoodreadsImportService
import org.booktower.services.JwtService
import org.booktower.services.LibraryHealthService
import org.booktower.services.LibraryService
import org.booktower.services.MagicShelfService
import org.booktower.services.PasswordResetService
import org.booktower.services.PdfMetadataService
import org.booktower.services.ReadingSessionService
import org.booktower.services.SeedService
import org.booktower.services.UserSettingsService
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
                    .body("""{"username":"$name","email":"$name@test.com","password":"password123"}"""),
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
        val userId = java.util.UUID.randomUUID()
        val now =
            java.time.Instant
                .now()
                .toString()
        jdbi.useHandle<Exception> { h ->
            h
                .createUpdate("INSERT INTO users (id,username,email,password_hash,created_at,updated_at,is_admin) VALUES (?,?,?,?,?,?,0)")
                .bind(0, userId.toString())
                .bind(1, "hlthpath_${System.nanoTime()}")
                .bind(2, "hp_${System.nanoTime()}@t.com")
                .bind(3, "h")
                .bind(4, now)
                .bind(5, now)
                .execute()
            val libId =
                java.util.UUID
                    .randomUUID()
                    .toString()
            h
                .createUpdate("INSERT INTO libraries (id,user_id,name,path,created_at) VALUES (?,?,?,?,?)")
                .bind(0, libId)
                .bind(1, userId.toString())
                .bind(2, "BadPath")
                .bind(3, "/this/path/absolutely/does/not/exist/${System.nanoTime()}")
                .bind(4, now)
                .execute()
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
        val userId1 = java.util.UUID.randomUUID()
        val userId2 = java.util.UUID.randomUUID()
        val now =
            java.time.Instant
                .now()
                .toString()
        jdbi.useHandle<Exception> { h ->
            for ((uid, uname) in listOf(userId1 to "hlthuser1_${System.nanoTime()}", userId2 to "hlthuser2_${System.nanoTime()}")) {
                h
                    .createUpdate(
                        "INSERT INTO users (id,username,email,password_hash,created_at,updated_at,is_admin) VALUES (?,?,?,?,?,?,0)",
                    ).bind(0, uid.toString())
                    .bind(1, uname)
                    .bind(2, "$uname@t.com")
                    .bind(3, "h")
                    .bind(4, now)
                    .bind(5, now)
                    .execute()
                val libId =
                    java.util.UUID
                        .randomUUID()
                        .toString()
                h
                    .createUpdate("INSERT INTO libraries (id,user_id,name,path,created_at) VALUES (?,?,?,?,?)")
                    .bind(0, libId)
                    .bind(1, uid.toString())
                    .bind(2, "lib")
                    .bind(3, "/tmp/$libId")
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
