package org.booktower.services

import org.booktower.TestFixture
import org.booktower.models.CreateBookRequest
import org.booktower.models.CreateLibraryRequest
import org.booktower.models.CreateMagicShelfRequest
import org.booktower.models.CreateUserRequest
import org.booktower.models.ShelfRuleType
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MagicShelfServiceTest {
    private lateinit var magicShelfService: MagicShelfService
    private lateinit var bookService: BookService
    private lateinit var libraryService: LibraryService
    private lateinit var authService: AuthService
    private lateinit var jwtService: JwtService
    private lateinit var userId: UUID
    private lateinit var libId: String

    @BeforeEach
    fun setup() {
        val jdbi = TestFixture.database.getJdbi()
        val config = TestFixture.config
        jwtService = JwtService(config.security)
        authService = AuthService(jdbi, jwtService)
        val pdfMetadataService = PdfMetadataService(jdbi, config.storage.coversPath)
        libraryService = LibraryService(jdbi, pdfMetadataService)
        bookService = BookService(jdbi)
        magicShelfService = MagicShelfService(jdbi, bookService)

        val result = authService.register(
            CreateUserRequest("shelf_${System.nanoTime()}", "shelf_${System.nanoTime()}@test.com", "password123"),
        )
        userId = jwtService.extractUserId(result.getOrThrow().token)!!
        libId = libraryService.createLibrary(userId, CreateLibraryRequest("Shelf Lib", "./data/sl-${System.nanoTime()}")).id
    }

    @Test
    fun `createShelf persists and returns shelf`() {
        val shelf = magicShelfService.createShelf(
            userId,
            CreateMagicShelfRequest("Reading Now", ShelfRuleType.STATUS, "READING"),
        )
        assertEquals("Reading Now", shelf.name)
        assertEquals(ShelfRuleType.STATUS, shelf.ruleType)
        assertEquals("READING", shelf.ruleValue)
        assertTrue(shelf.id.isNotBlank())
    }

    @Test
    fun `getShelves returns created shelves`() {
        magicShelfService.createShelf(userId, CreateMagicShelfRequest("Shelf A", ShelfRuleType.STATUS, "READING"))
        magicShelfService.createShelf(userId, CreateMagicShelfRequest("Shelf B", ShelfRuleType.TAG, "sci-fi"))
        val shelves = magicShelfService.getShelves(userId)
        val names = shelves.map { it.name }
        assertTrue(names.contains("Shelf A"))
        assertTrue(names.contains("Shelf B"))
    }

    @Test
    fun `getShelves returns empty for user with no shelves`() {
        val otherResult = authService.register(
            CreateUserRequest("shelfother_${System.nanoTime()}", "shelfother_${System.nanoTime()}@test.com", "password123"),
        )
        val otherId = jwtService.extractUserId(otherResult.getOrThrow().token)!!
        assertTrue(magicShelfService.getShelves(otherId).isEmpty())
    }

    @Test
    fun `getShelf returns shelf by id`() {
        val created = magicShelfService.createShelf(userId, CreateMagicShelfRequest("By ID", ShelfRuleType.STATUS, "FINISHED"))
        val found = magicShelfService.getShelf(userId, UUID.fromString(created.id))
        assertNotNull(found)
        assertEquals("By ID", found.name)
    }

    @Test
    fun `getShelf returns null for wrong user`() {
        val created = magicShelfService.createShelf(userId, CreateMagicShelfRequest("Mine", ShelfRuleType.STATUS, "READING"))
        val otherResult = authService.register(
            CreateUserRequest("shelfwrong_${System.nanoTime()}", "shelfwrong_${System.nanoTime()}@test.com", "password123"),
        )
        val otherId = jwtService.extractUserId(otherResult.getOrThrow().token)!!
        assertNull(magicShelfService.getShelf(otherId, UUID.fromString(created.id)))
    }

    @Test
    fun `deleteShelf removes shelf`() {
        val shelf = magicShelfService.createShelf(userId, CreateMagicShelfRequest("Delete Me", ShelfRuleType.STATUS, "READING"))
        assertTrue(magicShelfService.deleteShelf(userId, UUID.fromString(shelf.id)))
        assertNull(magicShelfService.getShelf(userId, UUID.fromString(shelf.id)))
    }

    @Test
    fun `deleteShelf returns false for non-existent id`() {
        assertFalse(magicShelfService.deleteShelf(userId, UUID.randomUUID()))
    }

    @Test
    fun `deleteShelf returns false for wrong user`() {
        val shelf = magicShelfService.createShelf(userId, CreateMagicShelfRequest("Protected", ShelfRuleType.STATUS, "READING"))
        val otherResult = authService.register(
            CreateUserRequest("shelfprot_${System.nanoTime()}", "shelfprot_${System.nanoTime()}@test.com", "password123"),
        )
        val otherId = jwtService.extractUserId(otherResult.getOrThrow().token)!!
        assertFalse(magicShelfService.deleteShelf(otherId, UUID.fromString(shelf.id)))
    }

    @Test
    fun `STATUS shelf resolves books with matching status`() {
        val book = bookService.createBook(userId, CreateBookRequest("Reading Book", "Auth", null, libId)).getOrThrow()
        bookService.bulkStatus(userId, listOf(UUID.fromString(book.id)), "READING")
        val shelf = magicShelfService.createShelf(userId, CreateMagicShelfRequest("Reading", ShelfRuleType.STATUS, "READING"))
        val books = magicShelfService.resolveBooks(userId, shelf)
        assertTrue(books.any { it.id == book.id })
    }

    @Test
    fun `STATUS shelf does not include books with different status`() {
        val book = bookService.createBook(userId, CreateBookRequest("Finished Book", "Auth", null, libId)).getOrThrow()
        bookService.bulkStatus(userId, listOf(UUID.fromString(book.id)), "FINISHED")
        val shelf = magicShelfService.createShelf(userId, CreateMagicShelfRequest("Reading Only", ShelfRuleType.STATUS, "READING"))
        val books = magicShelfService.resolveBooks(userId, shelf)
        assertTrue(books.none { it.id == book.id })
    }

    @Test
    fun `TAG shelf resolves books with matching tag`() {
        val book = bookService.createBook(userId, CreateBookRequest("Tagged Book", "Auth", null, libId)).getOrThrow()
        bookService.bulkTag(userId, listOf(UUID.fromString(book.id)), listOf("sci-fi"))
        val shelf = magicShelfService.createShelf(userId, CreateMagicShelfRequest("Sci-Fi", ShelfRuleType.TAG, "sci-fi"))
        val books = magicShelfService.resolveBooks(userId, shelf)
        assertTrue(books.any { it.id == book.id })
    }

    @Test
    fun `STATUS shelf bookCount reflects matching books`() {
        val book = bookService.createBook(userId, CreateBookRequest("Want Book", "Auth", null, libId)).getOrThrow()
        bookService.bulkStatus(userId, listOf(UUID.fromString(book.id)), "WANT_TO_READ")
        val shelf = magicShelfService.createShelf(userId, CreateMagicShelfRequest("Want", ShelfRuleType.STATUS, "WANT_TO_READ"))
        assertEquals(1, shelf.bookCount)
    }
}
