package org.booktower.integration

import org.booktower.TestFixture
import org.booktower.config.Json
import org.booktower.config.SmtpConfig
import org.booktower.config.WeblateConfig
import org.booktower.filters.GlobalErrorFilter
import org.booktower.handlers.AppHandler
import org.booktower.models.LoginResponse
import org.booktower.services.AdminService
import org.booktower.services.AnalyticsService
import org.booktower.services.AnnotationService
import org.booktower.services.ApiTokenService
import org.booktower.services.AuthService
import org.booktower.services.BookmarkService
import org.booktower.services.BookService
import org.booktower.services.ComicService
import org.booktower.services.EmailService
import org.booktower.services.EpubMetadataService
import org.booktower.services.ExportService
import org.booktower.services.GoodreadsImportService
import org.booktower.services.JwtService
import org.booktower.services.KoboSyncService
import org.booktower.services.LibraryService
import org.booktower.services.MagicShelfService
import org.booktower.services.MetadataFetchService
import org.booktower.services.PasswordResetService
import org.booktower.services.PdfMetadataService
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
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class KoboSyncIntegrationTest {

    private val config = TestFixture.config
    private val jdbi = TestFixture.database.getJdbi()

    private lateinit var app: HttpHandler
    private lateinit var koboSyncService: KoboSyncService

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
        koboSyncService = KoboSyncService(jdbi, bookService, "http://localhost:9999")
        val appHandler = AppHandler(
            authService, libraryService, bookService, bookmarkService,
            userSettingsService, pdfMetadataService, epubMetadataService, adminService, jwtService, config.storage,
            TestFixture.templateRenderer,
            WeblateHandler(WeblateConfig("", "", "", false)),
            analyticsService, annotationService, MetadataFetchService(), magicShelfService,
            passwordResetService, EmailService(SmtpConfig("", 587, "", "", "", false)),
            "http://localhost:9999", true,
            apiTokenService, exportService, comicService, goodreadsImportService,
            readingSessionService, seedService,
            null, null, null, null, null, null, null, null, null, null, null, null,
            koboSyncService,
        )
        app = GlobalErrorFilter().then(appHandler.routes())
    }

    private fun registerAndToken(): String {
        val u = "kobo_${System.nanoTime()}"
        val r = app(Request(Method.POST, "/auth/register").header("Content-Type", "application/json")
            .body("""{"username":"$u","email":"$u@test.com","password":"pass1234"}"""))
        return Json.mapper.readValue(r.bodyString(), LoginResponse::class.java).token
    }

    @Test
    fun `POST api kobo devices registers device and returns token`() {
        val token = registerAndToken()
        val resp = app(Request(Method.POST, "/api/kobo/devices")
            .header("Cookie", "token=$token")
            .header("Content-Type", "application/json")
            .body("""{"deviceName":"My Kobo Clara"}"""))
        assertEquals(Status.CREATED, resp.status)
        val tree = Json.mapper.readTree(resp.bodyString())
        assertNotNull(tree.get("token")?.asText())
        assertEquals("My Kobo Clara", tree.get("deviceName")?.asText())
    }

    @Test
    fun `GET api kobo devices lists registered devices`() {
        val token = registerAndToken()
        app(Request(Method.POST, "/api/kobo/devices")
            .header("Cookie", "token=$token").header("Content-Type", "application/json")
            .body("""{"deviceName":"Device A"}"""))
        val resp = app(Request(Method.GET, "/api/kobo/devices").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        val arr = Json.mapper.readTree(resp.bodyString())
        assertTrue(arr.isArray && arr.size() >= 1)
    }

    @Test
    fun `GET kobo token initialization returns device config`() {
        val token = registerAndToken()
        val regResp = app(Request(Method.POST, "/api/kobo/devices")
            .header("Cookie", "token=$token").header("Content-Type", "application/json")
            .body("""{"deviceName":"Kobo"}"""))
        val deviceToken = Json.mapper.readTree(regResp.bodyString()).get("token").asText()

        val resp = app(Request(Method.GET, "/kobo/$deviceToken/v1/initialization"))
        assertEquals(Status.OK, resp.status)
        val tree = Json.mapper.readTree(resp.bodyString())
        assertNotNull(tree.get("Resources"))
    }

    @Test
    fun `GET kobo initialization with invalid token returns 401`() {
        val resp = app(Request(Method.GET, "/kobo/bad-token/v1/initialization"))
        assertEquals(Status.UNAUTHORIZED, resp.status)
    }

    @Test
    fun `POST kobo token library sync returns book list`() {
        val token = registerAndToken()
        val regResp = app(Request(Method.POST, "/api/kobo/devices")
            .header("Cookie", "token=$token").header("Content-Type", "application/json")
            .body("""{"deviceName":"Kobo"}"""))
        val deviceToken = Json.mapper.readTree(regResp.bodyString()).get("token").asText()

        val resp = app(Request(Method.POST, "/kobo/$deviceToken/v1/library/sync"))
        assertEquals(Status.OK, resp.status)
        val tree = Json.mapper.readTree(resp.bodyString())
        assertTrue(tree.has("BookEntitlements"))
        assertTrue(tree.has("SyncToken"))
        assertEquals(false, tree.get("Continues")?.asBoolean())
    }

    @Test
    fun `DELETE api kobo devices removes device`() {
        val token = registerAndToken()
        val regResp = app(Request(Method.POST, "/api/kobo/devices")
            .header("Cookie", "token=$token").header("Content-Type", "application/json")
            .body("""{"deviceName":"Kobo"}"""))
        val deviceToken = Json.mapper.readTree(regResp.bodyString()).get("token").asText()

        val del = app(Request(Method.DELETE, "/api/kobo/devices/$deviceToken").header("Cookie", "token=$token"))
        assertEquals(Status.NO_CONTENT, del.status)

        // Device should no longer work for sync
        val initResp = app(Request(Method.GET, "/kobo/$deviceToken/v1/initialization"))
        assertEquals(Status.UNAUTHORIZED, initResp.status)
    }

    @Test
    fun `PUT kobo reading-state updates book progress`() {
        val token = registerAndToken()
        // Create a library and book so progress can be updated
        val libResp = app(Request(Method.POST, "/api/libraries")
            .header("Cookie", "token=$token").header("Content-Type", "application/json")
            .body("""{"name":"KoboLib","path":"./data/kobo-test"}"""))
        val libId = Json.mapper.readTree(libResp.bodyString()).get("id").asText()
        val bookResp = app(Request(Method.POST, "/api/books")
            .header("Cookie", "token=$token").header("Content-Type", "application/json")
            .body("""{"title":"Kobo Book","libraryId":"$libId"}"""))
        val bookId = Json.mapper.readTree(bookResp.bodyString()).get("id").asText()

        val regResp = app(Request(Method.POST, "/api/kobo/devices")
            .header("Cookie", "token=$token").header("Content-Type", "application/json")
            .body("""{"deviceName":"Kobo"}"""))
        val deviceToken = Json.mapper.readTree(regResp.bodyString()).get("token").asText()

        val resp = app(Request(Method.PUT, "/kobo/$deviceToken/v1/library/$bookId/reading-state")
            .header("Content-Type", "application/json")
            .body("""{"CurrentBookmark":{"ContentSourceProgressPercent":0.42,"Location":"5","LocationType":"CFI"}}"""))
        assertEquals(Status.OK, resp.status)
        val tree = Json.mapper.readTree(resp.bodyString())
        assertEquals("RequestAccepted", tree.get("RequestResult")?.asText())
    }

    @Test
    fun `PUT kobo reading-state with invalid device token returns 401`() {
        val resp = app(Request(Method.PUT, "/kobo/bad-token/v1/library/00000000-0000-0000-0000-000000000000/reading-state")
            .header("Content-Type", "application/json")
            .body("""{"CurrentBookmark":{"ContentSourceProgressPercent":0.5}}"""))
        assertEquals(Status.UNAUTHORIZED, resp.status)
    }

    @Test
    fun `POST api kobo devices requires authentication`() {
        val resp = app(Request(Method.POST, "/api/kobo/devices")
            .header("Content-Type", "application/json").body("""{"deviceName":"x"}"""))
        assertEquals(Status.UNAUTHORIZED, resp.status)
    }
}
