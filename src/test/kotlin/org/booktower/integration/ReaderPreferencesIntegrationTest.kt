package org.booktower.integration

import org.booktower.TestFixture
import org.booktower.config.Json
import org.booktower.config.SmtpConfig
import org.booktower.config.WeblateConfig
import org.booktower.filters.globalErrorFilter
import org.booktower.handlers.AppHandler
import org.booktower.models.LoginResponse
import org.booktower.services.AdminService
import org.booktower.services.AnalyticsService
import org.booktower.services.AnnotationService
import org.booktower.services.ApiTokenService
import org.booktower.services.AuthService
import org.booktower.services.BookService
import org.booktower.services.BookmarkService
import org.booktower.services.ComicService
import org.booktower.services.EmailService
import org.booktower.services.EpubMetadataService
import org.booktower.services.ExportService
import org.booktower.services.GoodreadsImportService
import org.booktower.services.JwtService
import org.booktower.services.LibraryService
import org.booktower.services.MagicShelfService
import org.booktower.services.MetadataFetchService
import org.booktower.services.PasswordResetService
import org.booktower.services.PdfMetadataService
import org.booktower.services.ReaderPreferencesService
import org.booktower.services.ReadingSessionService
import org.booktower.services.SeedService
import org.booktower.services.UserSettingsService
import org.booktower.weblate.WeblateHandler
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.core.then
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ReaderPreferencesIntegrationTest {
    private val config = TestFixture.config
    private val jdbi = TestFixture.database.getJdbi()
    private lateinit var app: HttpHandler

    @BeforeEach
    fun setup() {
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
        val readerPreferencesService = ReaderPreferencesService(userSettingsService)
        val appHandler =
            AppHandler(
                authService,
                libraryService,
                bookService,
                bookmarkService,
                userSettingsService,
                pdfMetadataService,
                epubMetadataService,
                adminService,
                jwtService,
                config.storage,
                TestFixture.templateRenderer,
                WeblateHandler(WeblateConfig("", "", "", false)),
                analyticsService,
                annotationService,
                MetadataFetchService(),
                magicShelfService,
                passwordResetService,
                EmailService(SmtpConfig("", 587, "", "", "", false)),
                "http://localhost:9999",
                true,
                apiTokenService,
                exportService,
                comicService,
                goodreadsImportService,
                readingSessionService,
                seedService,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                readerPreferencesService,
            )
        app = globalErrorFilter().then(appHandler.routes())
    }

    private fun registerAndToken(): String {
        val u = "rp_${System.nanoTime()}"
        val r =
            app(
                Request(Method.POST, "/auth/register")
                    .header("Content-Type", "application/json")
                    .body("""{"username":"$u","email":"$u@test.com","password":"pass1234"}"""),
            )
        return Json.mapper.readValue(r.bodyString(), LoginResponse::class.java).token
    }

    @Test
    fun `GET reader-preferences returns empty map when not set`() {
        val token = registerAndToken()
        val resp = app(Request(Method.GET, "/api/reader-preferences/epub").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        val tree = Json.mapper.readTree(resp.bodyString())
        assertTrue(tree.isObject && tree.size() == 0)
    }

    @Test
    fun `PUT reader-preferences stores preferences for epub`() {
        val token = registerAndToken()
        val resp =
            app(
                Request(Method.PUT, "/api/reader-preferences/epub")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"fontSize":18,"theme":"sepia","fontFamily":"Georgia"}"""),
            )
        assertEquals(Status.OK, resp.status)
        val tree = Json.mapper.readTree(resp.bodyString())
        assertEquals(18, tree.get("fontSize")?.asInt())
        assertEquals("sepia", tree.get("theme")?.asText())
    }

    @Test
    fun `GET reader-preferences returns previously saved prefs`() {
        val token = registerAndToken()
        app(
            Request(Method.PUT, "/api/reader-preferences/pdf")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"scrollMode":"continuous","zoom":1.5}"""),
        )
        val resp = app(Request(Method.GET, "/api/reader-preferences/pdf").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        val tree = Json.mapper.readTree(resp.bodyString())
        assertEquals("continuous", tree.get("scrollMode")?.asText())
    }

    @Test
    fun `PATCH reader-preferences merges with existing prefs`() {
        val token = registerAndToken()
        app(
            Request(Method.PUT, "/api/reader-preferences/cbz")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"fitMode":"width","direction":"ltr"}"""),
        )
        val resp =
            app(
                Request(Method.PATCH, "/api/reader-preferences/cbz")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"direction":"rtl"}"""),
            )
        assertEquals(Status.OK, resp.status)
        val tree = Json.mapper.readTree(resp.bodyString())
        assertEquals("width", tree.get("fitMode")?.asText()) // retained
        assertEquals("rtl", tree.get("direction")?.asText()) // updated
    }

    @Test
    fun `DELETE reader-preferences resets to empty`() {
        val token = registerAndToken()
        app(
            Request(Method.PUT, "/api/reader-preferences/epub")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"fontSize":20}"""),
        )
        val del = app(Request(Method.DELETE, "/api/reader-preferences/epub").header("Cookie", "token=$token"))
        assertEquals(Status.NO_CONTENT, del.status)
        val get = app(Request(Method.GET, "/api/reader-preferences/epub").header("Cookie", "token=$token"))
        val tree = Json.mapper.readTree(get.bodyString())
        assertTrue(tree.size() == 0)
    }

    @Test
    fun `preferences are isolated per user`() {
        val token1 = registerAndToken()
        val token2 = registerAndToken()
        app(
            Request(Method.PUT, "/api/reader-preferences/epub")
                .header("Cookie", "token=$token1")
                .header("Content-Type", "application/json")
                .body("""{"fontSize":24}"""),
        )
        val resp2 = app(Request(Method.GET, "/api/reader-preferences/epub").header("Cookie", "token=$token2"))
        val tree = Json.mapper.readTree(resp2.bodyString())
        assertTrue(tree.size() == 0)
    }

    @Test
    fun `reader-preferences endpoints require authentication`() {
        val resp = app(Request(Method.GET, "/api/reader-preferences/epub"))
        assertEquals(Status.UNAUTHORIZED, resp.status)
    }

    @Test
    fun `preferences are isolated per device`() {
        val token = registerAndToken()
        app(
            Request(Method.PUT, "/api/reader-preferences/epub?device=aabbccdd")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"fontSize":22}"""),
        )
        app(
            Request(Method.PUT, "/api/reader-preferences/epub?device=11223344")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"fontSize":14}"""),
        )

        val resp1 =
            app(
                Request(Method.GET, "/api/reader-preferences/epub?device=aabbccdd")
                    .header("Cookie", "token=$token"),
            )
        val resp2 =
            app(
                Request(Method.GET, "/api/reader-preferences/epub?device=11223344")
                    .header("Cookie", "token=$token"),
            )

        assertEquals(
            22,
            Json.mapper
                .readTree(resp1.bodyString())
                .get("fontSize")
                ?.asInt(),
        )
        assertEquals(
            14,
            Json.mapper
                .readTree(resp2.bodyString())
                .get("fontSize")
                ?.asInt(),
        )
    }

    @Test
    fun `device preferences are independent from global preferences`() {
        val token = registerAndToken()
        app(
            Request(Method.PUT, "/api/reader-preferences/epub")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"fontSize":16}"""),
        )
        app(
            Request(Method.PUT, "/api/reader-preferences/epub?device=aabbccdd")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"fontSize":20}"""),
        )

        val global = app(Request(Method.GET, "/api/reader-preferences/epub").header("Cookie", "token=$token"))
        val device = app(Request(Method.GET, "/api/reader-preferences/epub?device=aabbccdd").header("Cookie", "token=$token"))

        assertEquals(
            16,
            Json.mapper
                .readTree(global.bodyString())
                .get("fontSize")
                ?.asInt(),
        )
        assertEquals(
            20,
            Json.mapper
                .readTree(device.bodyString())
                .get("fontSize")
                ?.asInt(),
        )
    }
}
