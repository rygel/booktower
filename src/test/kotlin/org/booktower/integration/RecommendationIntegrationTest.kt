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
import org.booktower.services.LibraryService
import org.booktower.services.MagicShelfService
import org.booktower.services.PasswordResetService
import org.booktower.services.PdfMetadataService
import org.booktower.services.ReadingSessionService
import org.booktower.services.RecommendationService
import org.booktower.services.SeedService
import org.booktower.services.UserSettingsService
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RecommendationIntegrationTest {
    private val config = TestFixture.config
    private val jdbi = TestFixture.database.getJdbi()
    private val recommendationService = RecommendationService(jdbi)

    private lateinit var app: HttpHandler

    @BeforeEach
    fun setup() {
        app = buildApp()
    }

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
            recommendationService = recommendationService,
        )
    }

    private fun registerAndGetToken(prefix: String = "rec"): String {
        val u = "${prefix}_${System.nanoTime()}"
        val resp =
            app(
                Request(Method.POST, "/auth/register")
                    .header("Content-Type", "application/json")
                    .body("""{"username":"$u","email":"$u@test.com","password":"password123"}"""),
            )
        return Json.mapper.readValue(resp.bodyString(), LoginResponse::class.java).token
    }

    private fun createLibrary(token: String): String {
        val resp =
            app(
                Request(Method.POST, "/api/libraries")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"name":"Lib ${System.nanoTime()}","path":"./data/rec-${System.nanoTime()}"}"""),
            )
        return Json.mapper
            .readTree(resp.bodyString())
            .get("id")
            .asText()
    }

    private fun createBook(
        token: String,
        libId: String,
        title: String,
        author: String? = null,
    ): String {
        val authorJson = if (author != null) """"$author"""" else "null"
        val resp =
            app(
                Request(Method.POST, "/api/books")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"title":"$title","author":$authorJson,"description":null,"libraryId":"$libId"}"""),
            )
        return Json.mapper
            .readTree(resp.bodyString())
            .get("id")
            .asText()
    }

    @Test
    fun `GET api books id similar returns empty list when no similar books exist`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Lonely Book")

        val resp = app(Request(Method.GET, "/api/books/$bookId/similar").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        val tree = Json.mapper.readTree(resp.bodyString())
        assertEquals(0, tree.get("similar").size())
    }

    @Test
    fun `GET api books id similar returns books by same author`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val sourceId = createBook(token, libId, "Dune", "Frank Herbert")
        createBook(token, libId, "Dune Messiah", "Frank Herbert")
        createBook(token, libId, "Children of Dune", "Frank Herbert")
        createBook(token, libId, "Other Book", "Isaac Asimov")

        val resp = app(Request(Method.GET, "/api/books/$sourceId/similar").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        val similar = Json.mapper.readTree(resp.bodyString()).get("similar")
        val titles = (0 until similar.size()).map { similar[it].get("title").asText() }
        assert(titles.contains("Dune Messiah")) { "Should include Dune Messiah" }
        assert(titles.contains("Children of Dune")) { "Should include Children of Dune" }
        assert(!titles.contains("Other Book")) { "Should not include Other Book" }
        assert(!titles.contains("Dune")) { "Should not include the source book" }
        similar.forEach { assertEquals("author", it.get("reason").asText()) }
    }

    @Test
    fun `GET api books id similar returns 401 without authentication`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Some Book")

        val resp = app(Request(Method.GET, "/api/books/$bookId/similar"))
        assertEquals(Status.UNAUTHORIZED, resp.status)
    }

    @Test
    fun `GET api books id similar does not include books from other users`() {
        val token1 = registerAndGetToken("rec1")
        val token2 = registerAndGetToken("rec2")
        val lib1 = createLibrary(token1)
        val lib2 = createLibrary(token2)

        val sourceId = createBook(token1, lib1, "Foundation", "Isaac Asimov")
        // User 2 also has books by Asimov — should NOT appear in user 1's recommendations
        createBook(token2, lib2, "I Robot", "Isaac Asimov")
        createBook(token1, lib1, "Foundation and Empire", "Isaac Asimov")

        val resp = app(Request(Method.GET, "/api/books/$sourceId/similar").header("Cookie", "token=$token1"))
        assertEquals(Status.OK, resp.status)
        val similar = Json.mapper.readTree(resp.bodyString()).get("similar")
        val titles = (0 until similar.size()).map { similar[it].get("title").asText() }
        assert(titles.contains("Foundation and Empire")) { "Own library result should be present" }
        assert(!titles.contains("I Robot")) { "Should not include books from another user" }
    }

    @Test
    fun `GET api books id similar returns 400 for invalid book id`() {
        val token = registerAndGetToken()
        val resp = app(Request(Method.GET, "/api/books/not-a-uuid/similar").header("Cookie", "token=$token"))
        assertEquals(Status.BAD_REQUEST, resp.status)
    }
}
