package org.booktower.services

import org.booktower.TestFixture
import org.booktower.models.CreateLibraryRequest
import org.booktower.models.CreateUserRequest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LibraryServiceTest {
    private lateinit var libraryService: LibraryService
    private lateinit var authService: AuthService
    private lateinit var jwtService: JwtService
    private lateinit var userId: UUID

    @BeforeEach
    fun setup() {
        val config = TestFixture.config
        val jdbi = TestFixture.database.getJdbi()
        jwtService = JwtService(config.security)
        authService = AuthService(jdbi, jwtService)
        val pdfMetadataService = PdfMetadataService(jdbi, config.storage.coversPath)
        libraryService = LibraryService(jdbi, pdfMetadataService)

        val result =
            authService.register(
                CreateUserRequest("libuser_${System.nanoTime()}", "lib_${System.nanoTime()}@test.com", "password123"),
            )
        userId = jwtService.extractUserId(result.getOrThrow().token)!!
    }

    @Test
    fun `getLibraries returns empty list for new user`() {
        assertTrue(libraryService.getLibraries(userId).isEmpty())
    }

    @Test
    fun `createLibrary creates and returns library`() {
        val library = libraryService.createLibrary(userId, CreateLibraryRequest("Test Library", "./data/test-lib-${System.nanoTime()}"))
        assertEquals("Test Library", library.name)
        assertEquals(0, library.bookCount)
        assertNotNull(library.id)
    }

    @Test
    fun `getLibraries returns created libraries`() {
        libraryService.createLibrary(userId, CreateLibraryRequest("Lib A", "./data/test-a-${System.nanoTime()}"))
        libraryService.createLibrary(userId, CreateLibraryRequest("Lib B", "./data/test-b-${System.nanoTime()}"))
        assertEquals(2, libraryService.getLibraries(userId).size)
    }

    @Test
    fun `getLibrary returns specific library`() {
        val created = libraryService.createLibrary(userId, CreateLibraryRequest("Specific", "./data/test-s-${System.nanoTime()}"))
        val found = libraryService.getLibrary(userId, UUID.fromString(created.id))
        assertNotNull(found)
        assertEquals("Specific", found.name)
    }

    @Test
    fun `getLibrary returns null for non-existent id`() {
        assertNull(libraryService.getLibrary(userId, UUID.randomUUID()))
    }

    @Test
    fun `deleteLibrary removes library`() {
        val created = libraryService.createLibrary(userId, CreateLibraryRequest("ToDelete", "./data/test-d-${System.nanoTime()}"))
        val libId = UUID.fromString(created.id)
        assertTrue(libraryService.deleteLibrary(userId, libId))
        assertNull(libraryService.getLibrary(userId, libId))
    }

    @Test
    fun `deleteLibrary returns false for non-existent library`() {
        assertFalse(libraryService.deleteLibrary(userId, UUID.randomUUID()))
    }

    @Test
    fun `libraries are isolated per user`() {
        libraryService.createLibrary(userId, CreateLibraryRequest("UserA Lib", "./data/test-ua-${System.nanoTime()}"))
        val otherResult =
            authService.register(
                CreateUserRequest("other_${System.nanoTime()}", "other_${System.nanoTime()}@test.com", "password123"),
            )
        val otherUserId = jwtService.extractUserId(otherResult.getOrThrow().token)!!
        assertTrue(libraryService.getLibraries(otherUserId).isEmpty())
    }
}
