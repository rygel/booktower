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

class AnnotationServiceTest {
    private lateinit var annotationService: AnnotationService
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
        val pdfMetadataService = PdfMetadataService(jdbi, config.storage.coversPath)
        libraryService = LibraryService(jdbi, pdfMetadataService)
        bookService = BookService(jdbi)
        annotationService = AnnotationService(jdbi)

        val result = authService.register(
            CreateUserRequest("ann_${System.nanoTime()}", "ann_${System.nanoTime()}@test.com", "password123"),
        )
        userId = jwtService.extractUserId(result.getOrThrow().token)!!
        val libId = libraryService.createLibrary(userId, CreateLibraryRequest("Ann Lib", "./data/ann-${System.nanoTime()}")).id
        val book = bookService.createBook(userId, CreateBookRequest("Annotation Book", "Author", null, libId)).getOrThrow()
        bookId = UUID.fromString(book.id)
    }

    @Test
    fun `createAnnotation returns annotation with correct fields`() {
        val ann = annotationService.createAnnotation(userId, bookId, 5, "highlighted text", "yellow")
        assertEquals(bookId.toString(), ann.bookId)
        assertEquals(5, ann.page)
        assertEquals("highlighted text", ann.selectedText)
        assertEquals("yellow", ann.color)
        assertTrue(ann.id.isNotBlank())
    }

    @Test
    fun `getAnnotations returns created annotations ordered by page`() {
        annotationService.createAnnotation(userId, bookId, 10, "second", "blue")
        annotationService.createAnnotation(userId, bookId, 3, "first", "yellow")
        val anns = annotationService.getAnnotations(userId, bookId)
        assertEquals(2, anns.size)
        assertEquals(3, anns[0].page)
        assertEquals(10, anns[1].page)
    }

    @Test
    fun `getAnnotations filtered by page returns only that page`() {
        annotationService.createAnnotation(userId, bookId, 1, "pg1", "yellow")
        annotationService.createAnnotation(userId, bookId, 2, "pg2", "blue")
        val anns = annotationService.getAnnotations(userId, bookId, page = 1)
        assertEquals(1, anns.size)
        assertEquals(1, anns[0].page)
    }

    @Test
    fun `getAnnotations returns empty list for book with no annotations`() {
        val anns = annotationService.getAnnotations(userId, bookId)
        assertTrue(anns.isEmpty())
    }

    @Test
    fun `deleteAnnotation removes it from the list`() {
        val ann = annotationService.createAnnotation(userId, bookId, 7, "delete me", "red")
        val deleted = annotationService.deleteAnnotation(userId, UUID.fromString(ann.id))
        assertTrue(deleted)
        assertTrue(annotationService.getAnnotations(userId, bookId).isEmpty())
    }

    @Test
    fun `deleteAnnotation returns false for non-existent id`() {
        assertFalse(annotationService.deleteAnnotation(userId, UUID.randomUUID()))
    }

    @Test
    fun `deleteAnnotation does not delete another user's annotation`() {
        val ann = annotationService.createAnnotation(userId, bookId, 3, "mine", "green")
        val otherResult = authService.register(
            CreateUserRequest("annother_${System.nanoTime()}", "annother_${System.nanoTime()}@test.com", "password123"),
        )
        val otherId = jwtService.extractUserId(otherResult.getOrThrow().token)!!
        val deleted = annotationService.deleteAnnotation(otherId, UUID.fromString(ann.id))
        assertFalse(deleted)
        assertEquals(1, annotationService.getAnnotations(userId, bookId).size)
    }

    @Test
    fun `selectedText is truncated to 2000 characters`() {
        val longText = "x".repeat(3000)
        val ann = annotationService.createAnnotation(userId, bookId, 1, longText, "yellow")
        assertEquals(2000, ann.selectedText.length)
    }

    @Test
    fun `color is truncated to 20 characters`() {
        val longColor = "a".repeat(50)
        val ann = annotationService.createAnnotation(userId, bookId, 1, "text", longColor)
        assertEquals(20, ann.color.length)
    }

    @Test
    fun `annotations are isolated between users`() {
        annotationService.createAnnotation(userId, bookId, 1, "user1 note", "yellow")
        val otherResult = authService.register(
            CreateUserRequest("anniso_${System.nanoTime()}", "anniso_${System.nanoTime()}@test.com", "password123"),
        )
        val otherId = jwtService.extractUserId(otherResult.getOrThrow().token)!!
        assertTrue(annotationService.getAnnotations(otherId, bookId).isEmpty())
    }
}
