package org.booktower.services

import org.booktower.TestFixture
import org.booktower.models.CreateBookRequest
import org.booktower.models.CreateLibraryRequest
import org.booktower.models.CreateUserRequest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AnalyticsServiceTest {
    private lateinit var analyticsService: AnalyticsService
    private lateinit var userSettingsService: UserSettingsService
    private lateinit var bookService: BookService
    private lateinit var libraryService: LibraryService
    private lateinit var authService: AuthService
    private lateinit var jwtService: JwtService
    private lateinit var userId: UUID
    private lateinit var bookId: UUID

    @BeforeEach
    fun setup() {
        val jdbi = TestFixture.database.getJdbi()
        val config = TestFixture.config
        jwtService = JwtService(config.security)
        authService = AuthService(jdbi, jwtService)
        userSettingsService = UserSettingsService(jdbi)
        analyticsService = AnalyticsService(jdbi, userSettingsService)
        val pdfMetadataService = PdfMetadataService(jdbi, config.storage.coversPath)
        libraryService = LibraryService(jdbi, pdfMetadataService)
        bookService = BookService(jdbi)

        val result = authService.register(
            CreateUserRequest("analytics_${System.nanoTime()}", "analytics_${System.nanoTime()}@test.com", "password123"),
        )
        userId = jwtService.extractUserId(result.getOrThrow().token)!!
        val libId = libraryService.createLibrary(userId, CreateLibraryRequest("Analytics Lib", "./data/al-${System.nanoTime()}")).id
        val book = bookService.createBook(userId, CreateBookRequest("Analytics Book", "Author", null, libId)).getOrThrow()
        bookId = UUID.fromString(book.id)
    }

    @Test
    fun `isEnabled returns false for new user`() {
        assertFalse(analyticsService.isEnabled(userId))
    }

    @Test
    fun `isEnabled returns true after enabling`() {
        userSettingsService.set(userId, "analytics.enabled", "true")
        assertTrue(analyticsService.isEnabled(userId))
    }

    @Test
    fun `recordProgress is a no-op when analytics disabled`() {
        analyticsService.recordProgress(userId, bookId, 10)
        val summary = analyticsService.getSummary(userId)
        assertEquals(0, summary.totalPages)
    }

    @Test
    fun `recordProgress is a no-op for zero pages`() {
        userSettingsService.set(userId, "analytics.enabled", "true")
        analyticsService.recordProgress(userId, bookId, 0)
        assertEquals(0, analyticsService.getSummary(userId).totalPages)
    }

    @Test
    fun `recordProgress accumulates pages for same day and book`() {
        userSettingsService.set(userId, "analytics.enabled", "true")
        analyticsService.recordProgress(userId, bookId, 15)
        analyticsService.recordProgress(userId, bookId, 10)
        assertEquals(25, analyticsService.getSummary(userId).totalPages)
    }

    @Test
    fun `getSummary returns zero totals for user with no data`() {
        val summary = analyticsService.getSummary(userId)
        assertEquals(0, summary.totalPages)
        assertEquals(0, summary.streak)
        assertEquals(0, summary.booksFinished)
        assertEquals(30, summary.pagesLast30Days.size)
    }

    @Test
    fun `getSummary totalPages reflects recorded progress`() {
        userSettingsService.set(userId, "analytics.enabled", "true")
        analyticsService.recordProgress(userId, bookId, 42)
        assertEquals(42, analyticsService.getSummary(userId).totalPages)
    }

    @Test
    fun `getSummary streak is 1 after reading today`() {
        userSettingsService.set(userId, "analytics.enabled", "true")
        analyticsService.recordProgress(userId, bookId, 5)
        assertEquals(1, analyticsService.getSummary(userId).streak)
    }

    @Test
    fun `getSummary pagesLast30Days has exactly 30 entries`() {
        val summary = analyticsService.getSummary(userId)
        assertEquals(30, summary.pagesLast30Days.size)
    }

    @Test
    fun `getSummary pagesLast30Days reflects recorded pages on today`() {
        userSettingsService.set(userId, "analytics.enabled", "true")
        analyticsService.recordProgress(userId, bookId, 20)
        val today = java.time.LocalDate.now().toString()
        val entry = analyticsService.getSummary(userId).pagesLast30Days.last()
        assertEquals(today, entry.date)
        assertEquals(20, entry.pages)
    }

    @Test
    fun `analytics are isolated between users`() {
        val otherResult = authService.register(
            CreateUserRequest("anaother_${System.nanoTime()}", "anaother_${System.nanoTime()}@test.com", "password123"),
        )
        val otherId = jwtService.extractUserId(otherResult.getOrThrow().token)!!
        userSettingsService.set(userId, "analytics.enabled", "true")
        analyticsService.recordProgress(userId, bookId, 100)
        assertEquals(0, analyticsService.getSummary(otherId).totalPages)
    }
}
