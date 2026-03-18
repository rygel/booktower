package org.booktower.integration

import org.booktower.TestFixture
import org.booktower.config.Json
import org.booktower.config.SmtpConfig
import org.booktower.config.WeblateConfig
import org.booktower.filters.globalErrorFilter
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
import org.booktower.services.JournalService
import org.booktower.services.JwtService
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
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class JournalIntegrationTest {
    private val config = TestFixture.config
    private val jdbi = TestFixture.database.getJdbi()

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
        val journalService = JournalService(jdbi)
        return buildTestApp(authService = authService, libraryService = libraryService, bookService = bookService, jwtService = jwtService, journalService = journalService)
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun registerAndLogin(
        app: HttpHandler,
        prefix: String = "jrn",
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

    private fun createLibrary(
        app: HttpHandler,
        token: String,
    ): String {
        val resp =
            app(
                Request(Method.POST, "/api/libraries")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"name":"JrnLib_${System.nanoTime()}","path":"./data/test-${System.nanoTime()}"}"""),
            )
        return Json.mapper
            .readTree(resp.bodyString())
            .get("id")
            .asText()
    }

    private fun createBook(
        app: HttpHandler,
        token: String,
        libId: String,
    ): String {
        val resp =
            app(
                Request(Method.POST, "/api/books")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"title":"Journal Book","author":null,"description":null,"libraryId":"$libId"}"""),
            )
        return Json.mapper
            .readTree(resp.bodyString())
            .get("id")
            .asText()
    }

    // ── end-to-end tests ─────────────────────────────────────────────────────

    @Test
    fun `full journal lifecycle - create, read, update, delete`() {
        val app = buildApp()
        val token = registerAndLogin(app)
        val libId = createLibrary(app, token)
        val bookId = createBook(app, token, libId)

        // 1. Initially empty
        val listResp = app(Request(Method.GET, "/api/books/$bookId/journal").header("Cookie", "token=$token"))
        assertEquals(Status.OK, listResp.status)
        assertEquals(0, Json.mapper.readTree(listResp.bodyString()).size())

        // 2. Create entry
        val createResp =
            app(
                Request(Method.POST, "/api/books/$bookId/journal")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"title":"Chapter 1 thoughts","content":"Really loved the opening scene."}"""),
            )
        assertEquals(Status.CREATED, createResp.status)
        val created = Json.mapper.readTree(createResp.bodyString())
        val entryId = created.get("id").asText()
        assertEquals("Chapter 1 thoughts", created.get("title").asText())
        assertEquals("Really loved the opening scene.", created.get("content").asText())

        // 3. List shows the entry
        val list2Resp = app(Request(Method.GET, "/api/books/$bookId/journal").header("Cookie", "token=$token"))
        val entries = Json.mapper.readTree(list2Resp.bodyString())
        assertEquals(1, entries.size())
        assertEquals(entryId, entries[0].get("id").asText())

        // 4. Update entry
        val updateResp =
            app(
                Request(Method.PUT, "/api/books/$bookId/journal/$entryId")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"title":"Updated title","content":"Updated content here."}"""),
            )
        assertEquals(Status.OK, updateResp.status)
        val updated = Json.mapper.readTree(updateResp.bodyString())
        assertEquals("Updated title", updated.get("title").asText())
        assertEquals("Updated content here.", updated.get("content").asText())

        // 5. Delete entry
        val delResp =
            app(
                Request(Method.DELETE, "/api/books/$bookId/journal/$entryId")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.NO_CONTENT, delResp.status)

        // 6. List is empty again
        val list3Resp = app(Request(Method.GET, "/api/books/$bookId/journal").header("Cookie", "token=$token"))
        assertEquals(0, Json.mapper.readTree(list3Resp.bodyString()).size())
    }

    @Test
    fun `GET api journal returns all entries across books`() {
        val app = buildApp()
        val token = registerAndLogin(app)
        val libId = createLibrary(app, token)
        val bookId1 = createBook(app, token, libId)
        val bookId2 = createBook(app, token, libId)

        app(
            Request(Method.POST, "/api/books/$bookId1/journal")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"title":null,"content":"Note for book 1"}"""),
        )
        app(
            Request(Method.POST, "/api/books/$bookId2/journal")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"title":null,"content":"Note for book 2"}"""),
        )

        val resp = app(Request(Method.GET, "/api/journal").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        val all = Json.mapper.readTree(resp.bodyString())
        assertTrue(all.size() >= 2)
        val contents = all.map { it.get("content").asText() }.toSet()
        assertTrue("Note for book 1" in contents)
        assertTrue("Note for book 2" in contents)
    }

    @Test
    fun `blank content is rejected with 400`() {
        val app = buildApp()
        val token = registerAndLogin(app)
        val libId = createLibrary(app, token)
        val bookId = createBook(app, token, libId)

        val resp =
            app(
                Request(Method.POST, "/api/books/$bookId/journal")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"title":"t","content":""}"""),
            )
        assertEquals(Status.BAD_REQUEST, resp.status)
    }

    @Test
    fun `user cannot read or delete another user's journal entry`() {
        val app = buildApp()
        val tokenA = registerAndLogin(app, "jrnA")
        val tokenB = registerAndLogin(app, "jrnB")
        val libId = createLibrary(app, tokenA)
        val bookId = createBook(app, tokenA, libId)

        val createResp =
            app(
                Request(Method.POST, "/api/books/$bookId/journal")
                    .header("Cookie", "token=$tokenA")
                    .header("Content-Type", "application/json")
                    .body("""{"title":null,"content":"Private note"}"""),
            )
        val entryId =
            Json.mapper
                .readTree(createResp.bodyString())
                .get("id")
                .asText()

        // User B tries to delete user A's entry — should get 404 (not found for that user)
        val delResp =
            app(
                Request(Method.DELETE, "/api/books/$bookId/journal/$entryId").header("Cookie", "token=$tokenB"),
            )
        assertEquals(Status.NOT_FOUND, delResp.status)

        // Entry still exists for user A
        val listResp = app(Request(Method.GET, "/api/books/$bookId/journal").header("Cookie", "token=$tokenA"))
        assertNotNull(Json.mapper.readTree(listResp.bodyString()).firstOrNull { it.get("id").asText() == entryId })
    }

    @Test
    fun `journal endpoints require authentication`() {
        val app = buildApp()
        val listResp = app(Request(Method.GET, "/api/books/some-id/journal"))
        assertEquals(Status.UNAUTHORIZED, listResp.status)
        val allResp = app(Request(Method.GET, "/api/journal"))
        assertEquals(Status.UNAUTHORIZED, allResp.status)
    }
}
