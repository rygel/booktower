package org.booktower.services

import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.util.*

/**
 * Unit tests for JwtService
 */
class JwtServiceTest {
    private lateinit var jwtService: JwtService
    private val secret = "test-secret-key-that-is-long-enough-for-hs256-algorithm-requirements"
    private val issuer = "booktower-test"

    @BeforeEach
    fun setUp() {
        jwtService = JwtService(secret, issuer)
    }

    @Nested
    inner class GenerateTokenTests {

        @Test
        fun `should generate valid JWT token`() {
            // Given
            val userId = UUID.randomUUID()
            val user = org.booktower.models.User(
                id = userId,
                username = "testuser",
                email = "test@example.com",
                passwordHash = "hash",
                createdAt = java.time.Instant.now(),
                updatedAt = java.time.Instant.now(),
                isAdmin = false
            )

            // When
            val token = jwtService.generateToken(user)

            // Then
            assertNotNull(token)
            assertTrue(token.isNotBlank())
            assertTrue(token.split(".").size == 3, "JWT should have 3 parts separated by dots")
        }
    }

    @Nested
    inner class ExtractUserIdTests {

        @Test
        fun `should extract user id from valid token`() {
            // Given
            val userId = UUID.randomUUID()
            val user = org.booktower.models.User(
                id = userId,
                username = "testuser",
                email = "test@example.com",
                passwordHash = "hash",
                createdAt = java.time.Instant.now(),
                updatedAt = java.time.Instant.now(),
                isAdmin = false
            )
            val token = jwtService.generateToken(user)

            // When
            val extractedUserId = jwtService.extractUserId(token)

            // Then
            assertNotNull(extractedUserId)
            assertEquals(userId, extractedUserId)
        }

        @Test
        fun `should return null for invalid token`() {
            // Given
            val invalidToken = "invalid.token.here"

            // When
            val result = jwtService.extractUserId(invalidToken)

            // Then
            assertNull(result)
        }

        @Test
        fun `should return null for empty token`() {
            // Given
            val emptyToken = ""

            // When
            val result = jwtService.extractUserId(emptyToken)

            // Then
            assertNull(result)
        }

        @Test
        fun `should return null for malformed token`() {
            // Given
            val malformedToken = "not-a-valid-jwt"

            // When
            val result = jwtService.extractUserId(malformedToken)

            // Then
            assertNull(result)
        }
    }

    @Nested
    inner class ValidateTokenTests {

        @Test
        fun `should return true for valid token`() {
            // Given
            val userId = UUID.randomUUID()
            val user = org.booktower.models.User(
                id = userId,
                username = "testuser",
                email = "test@example.com",
                passwordHash = "hash",
                createdAt = java.time.Instant.now(),
                updatedAt = java.time.Instant.now(),
                isAdmin = false
            )
            val token = jwtService.generateToken(user)

            // When
            val isValid = jwtService.validateToken(token)

            // Then
            assertTrue(isValid)
        }

        @Test
        fun `should return false for invalid token`() {
            // Given
            val invalidToken = "invalid.token.here"

            // When
            val isValid = jwtService.validateToken(invalidToken)

            // Then
            assertFalse(isValid)
        }

        @Test
        fun `should return false for tampered token`() {
            // Given
            val userId = UUID.randomUUID()
            val user = org.booktower.models.User(
                id = userId,
                username = "testuser",
                email = "test@example.com",
                passwordHash = "hash",
                createdAt = java.time.Instant.now(),
                updatedAt = java.time.Instant.now(),
                isAdmin = false
            )
            val validToken = jwtService.generateToken(user)
            val tamperedToken = validToken.substring(0, validToken.length - 5) + "XXXXX"

            // When
            val isValid = jwtService.validateToken(tamperedToken)

            // Then
            assertFalse(isValid)
        }

        @Test
        fun `should return false for token with wrong secret`() {
            // Given
            val userId = UUID.randomUUID()
            val user = org.booktower.models.User(
                id = userId,
                username = "testuser",
                email = "test@example.com",
                passwordHash = "hash",
                createdAt = java.time.Instant.now(),
                updatedAt = java.time.Instant.now(),
                isAdmin = false
            )
            val differentService = JwtService("different-secret-key-that-is-also-long-enough", issuer)
            val token = differentService.generateToken(user)

            // When
            val isValid = jwtService.validateToken(token)

            // Then
            assertFalse(isValid)
        }
    }

    @Nested
    inner class ExtractUsernameTests {

        @Test
        fun `should extract username from valid token`() {
            // Given
            val user = org.booktower.models.User(
                id = UUID.randomUUID(),
                username = "myusername",
                email = "test@example.com",
                passwordHash = "hash",
                createdAt = java.time.Instant.now(),
                updatedAt = java.time.Instant.now(),
                isAdmin = false
            )
            val token = jwtService.generateToken(user)

            // When
            val username = jwtService.extractUsername(token)

            // Then
            assertNotNull(username)
            assertEquals("myusername", username)
        }

        @Test
        fun `should return null for invalid token`() {
            // Given
            val invalidToken = "invalid.token"

            // When
            val username = jwtService.extractUsername(invalidToken)

            // Then
            assertNull(username)
        }
    }
}
