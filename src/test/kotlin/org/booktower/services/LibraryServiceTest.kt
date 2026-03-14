package org.booktower.services

import org.booktower.config.AppConfig
import org.booktower.config.Database
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
        val config = AppConfig.load()
        val database = Database.connect(config.database)
        jwtService = JwtService(config.security)
        authService = AuthService(database.getJdbi(), jwtService)
        libraryService = LibraryService(database.getJdbi(), config.storage)

        val result = authService.register(
            CreateUserRequest("libuser_${System.nanoTime()}", "lib_${System.nanoTime()}@test.com", "password123"),
        )
        userId = jwtService.extractUserId(result.getOrThrow().token)!!
    }

    @Test
    fun `getLibraries returns empty list for new user`() {
        val libraries = libraryService.getLibraries(userId)
        assertTrue(libraries.isEmpty())
    }

    @Test
    fun `createLibrary creates and returns library`() {
        val request = CreateLibraryRequest("Test Library", "./data/test-lib-${System.nanoTime()}")
        val library = libraryService.createLibrary(userId, request)

        assertEquals("Test Library", library.name)
        assertEquals(0, library.bookCount)
        assertNotNull(library.id)
    }

    @Test
    fun `getLibraries returns created libraries`() {
        libraryService.createLibrary(userId, CreateLibraryRequest("Lib A", "./data/test-a-${System.nanoTime()}"))
        libraryService.createLibrary(userId, CreateLibraryRequest("Lib B", "./data/test-b-${System.nanoTime()}"))

        val libraries = libraryService.getLibraries(userId)
        assertEquals(2, libraries.size)
    }

    @Test
    fun `getLibrary returns specific library`() {
        val created = libraryService.createLibrary(userId, CreateLibraryRequest("Specific", "./data/test-s-${System.nanoTime()}"))
        val libId = UUID.fromString(created.id)

        val found = libraryService.getLibrary(userId, libId)
        assertNotNull(found)
        assertEquals("Specific", found.name)
    }

    @Test
    fun `getLibrary returns null for non-existent id`() {
        val found = libraryService.getLibrary(userId, UUID.randomUUID())
        assertNull(found)
    }

    @Test
    fun `deleteLibrary removes library`() {
        val created = libraryService.createLibrary(userId, CreateLibraryRequest("ToDelete", "./data/test-d-${System.nanoTime()}"))
        val libId = UUID.fromString(created.id)

        val deleted = libraryService.deleteLibrary(userId, libId)
        assertTrue(deleted)

        val found = libraryService.getLibrary(userId, libId)
        assertNull(found)
    }

    @Test
    fun `deleteLibrary returns false for non-existent library`() {
        val deleted = libraryService.deleteLibrary(userId, UUID.randomUUID())
        assertFalse(deleted)
    }

    @Test
    fun `libraries are isolated per user`() {
        libraryService.createLibrary(userId, CreateLibraryRequest("UserA Lib", "./data/test-ua-${System.nanoTime()}"))

        val otherResult = authService.register(
            CreateUserRequest("other_${System.nanoTime()}", "other_${System.nanoTime()}@test.com", "password123"),
        )
        val otherUserId = jwtService.extractUserId(otherResult.getOrThrow().token)!!

        val otherLibraries = libraryService.getLibraries(otherUserId)
        assertTrue(otherLibraries.isEmpty())
    }
}
