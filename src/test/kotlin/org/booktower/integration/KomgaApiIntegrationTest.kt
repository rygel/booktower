package org.booktower.integration

import org.booktower.TestFixture
import org.booktower.config.Json
import org.booktower.models.LoginResponse
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
import org.booktower.services.KomgaApiService
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
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class KomgaApiIntegrationTest {
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
        val komgaApiService = KomgaApiService(jdbi, bookService, libraryService, "http://localhost:9999")
        app =
            buildTestApp(
                authService = authService,
                libraryService = libraryService,
                bookService = bookService,
                jwtService = jwtService,
                komgaApiService = komgaApiService,
            )
    }

    private fun registerAndToken(): String {
        val u = "komga_${System.nanoTime()}"
        val r =
            app(
                Request(Method.POST, "/auth/register")
                    .header("Content-Type", "application/json")
                    .body("""{"username":"$u","email":"$u@test.com","password":"pass1234"}"""),
            )
        return Json.mapper.readValue(r.bodyString(), LoginResponse::class.java).token
    }

    private fun createLibrary(token: String): String {
        val r =
            app(
                Request(Method.POST, "/api/libraries")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"name":"Lib ${System.nanoTime()}","path":"./data/test-${System.nanoTime()}"}"""),
            )
        return Json.mapper
            .readTree(r.bodyString())
            .get("id")
            .asText()
    }

    private fun createBook(
        token: String,
        libId: String,
        title: String = "Book ${System.nanoTime()}",
    ): String {
        val r =
            app(
                Request(Method.POST, "/api/books")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"title":"$title","author":"Author A","libraryId":"$libId"}"""),
            )
        return Json.mapper
            .readTree(r.bodyString())
            .get("id")
            .asText()
    }

    @Test
    fun `GET api v1 libraries returns library list`() {
        val token = registerAndToken()
        createLibrary(token)
        val resp = app(Request(Method.GET, "/api/v1/libraries").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        val arr = Json.mapper.readTree(resp.bodyString())
        assertTrue(arr.isArray && arr.size() >= 1)
        assertNotNull(arr[0].get("id"))
        assertNotNull(arr[0].get("name"))
    }

    @Test
    fun `GET api v1 libraries returns empty list for new user`() {
        val token = registerAndToken()
        val resp = app(Request(Method.GET, "/api/v1/libraries").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        val arr = Json.mapper.readTree(resp.bodyString())
        assertTrue(arr.isArray)
    }

    @Test
    fun `GET api v1 books returns paginated book list`() {
        val token = registerAndToken()
        val libId = createLibrary(token)
        createBook(token, libId, "Test Book Komga")
        val resp = app(Request(Method.GET, "/api/v1/books").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        val tree = Json.mapper.readTree(resp.bodyString())
        assertTrue(tree.has("content"))
        assertTrue(tree.get("content").isArray)
        assertTrue(tree.get("content").size() >= 1)
        assertNotNull(tree.get("totalElements"))
    }

    @Test
    fun `GET api v1 books id returns book detail`() {
        val token = registerAndToken()
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Komga Book")
        val resp = app(Request(Method.GET, "/api/v1/books/$bookId").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        val tree = Json.mapper.readTree(resp.bodyString())
        assertEquals(bookId, tree.get("id")?.asText())
        assertEquals("Komga Book", tree.get("name")?.asText())
        assertTrue(tree.has("metadata"))
        assertTrue(tree.has("media"))
        assertTrue(tree.has("readProgress"))
    }

    @Test
    fun `GET api v1 books unknown id returns 404`() {
        val token = registerAndToken()
        val resp = app(Request(Method.GET, "/api/v1/books/00000000-0000-0000-0000-000000000000").header("Cookie", "token=$token"))
        assertEquals(Status.NOT_FOUND, resp.status)
    }

    @Test
    fun `GET api v1 series returns series grouped from book series field`() {
        val token = registerAndToken()
        val libId = createLibrary(token)
        // Create books with series metadata via PUT
        val bookId = createBook(token, libId, "Foundation")
        app(
            Request(Method.PUT, "/api/books/$bookId")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"title":"Foundation","author":"Isaac Asimov","series":"Foundation","seriesIndex":1.0}"""),
        )
        val resp = app(Request(Method.GET, "/api/v1/series").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        val tree = Json.mapper.readTree(resp.bodyString())
        assertTrue(tree.has("content"))
        assertTrue(tree.has("totalElements"))
    }

    @Test
    fun `Komga endpoints require authentication`() {
        val resp = app(Request(Method.GET, "/api/v1/libraries"))
        assertEquals(Status.UNAUTHORIZED, resp.status)
    }
}
