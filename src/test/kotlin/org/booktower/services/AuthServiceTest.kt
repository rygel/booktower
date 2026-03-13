package org.booktower.services

import io.mockk.*
import org.booktower.models.*
import org.jdbi.v3.core.Handle
import org.jdbi.v3.core.Jdbi
import org.jdbi.v3.core.result.ResultIterable
import org.junit.jupiter.api.*
import org.mindrot.jbcrypt.BCrypt
import java.time.Instant
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for AuthService
 */
class AuthServiceTest {
    private lateinit var jdbi: Jdbi
    private lateinit var jwtService: JwtService
    private lateinit var authService: AuthService
    private lateinit var handle: Handle

    @BeforeEach
    fun setUp() {
        jdbi = mockk()
        jwtService = mockk()
        handle = mockk()
        authService = AuthService(jdbi, jwtService)
    }

    @Nested
    inner class RegisterTests {

        @Test
        fun `should register new user successfully`() {
            // Given
            val request = CreateUserRequest(
                username = "testuser",
                email = "test@example.com",
                password = "password123"
            )
            
            every { jdbi.useHandle(any<
                (Handle) -> Unit
            >()) } answers {
                val callback = firstArg<(Handle) -> Unit>()
                callback(handle)
            }
            
            every { handle.createQuery(any()) } returns mockk {
                every { bind(0, request.username) } returns this
                every { mapTo(String::class.java) } returns mockk {
                    every { first() } returns null
                }
            }
            
            every { handle.createUpdate(any()) } returns mockk {
                every { bind(any<Int>(), any()) } returns this
                every { execute() } returns 1
            }
            
            every { jwtService.generateToken(any()) } returns "test-jwt-token"

            // When
            val result = authService.register(request)

            // Then
            assertTrue(result.isSuccess)
            val loginResponse = result.getOrNull()
            assertNotNull(loginResponse)
            assertEquals("test-jwt-token", loginResponse.token)
            assertEquals("testuser", loginResponse.user.username)
            assertEquals("test@example.com", loginResponse.user.email)
        }

        @Test
        fun `should fail when username already exists`() {
            // Given
            val request = CreateUserRequest(
                username = "existinguser",
                email = "new@example.com",
                password = "password123"
            )
            
            every { jdbi.useHandle(any<
                (Handle) -> Unit
            >()) } answers {
                val callback = firstArg<(Handle) -> Unit>()
                callback(handle)
            }
            
            every { handle.createQuery(any()) } returns mockk {
                every { bind(0, request.username) } returns this
                every { mapTo(String::class.java) } returns mockk {
                    every { first() } returns "existing-user-id"
                }
            }

            // When
            val result = authService.register(request)

            // Then
            assertTrue(result.isFailure)
            val exception = result.exceptionOrNull()
            assertNotNull(exception)
            assertTrue(exception is IllegalArgumentException)
            assertTrue(exception.message?.contains("already exists") == true)
        }

        @Test
        fun `should hash password before storing`() {
            // Given
            val request = CreateUserRequest(
                username = "testuser",
                email = "test@example.com",
                password = "mysecretpassword"
            )
            
            every { jdbi.useHandle(any<
                (Handle) -> Unit
            >()) } answers {
                val callback = firstArg<(Handle) -> Unit>()
                callback(handle)
            }
            
            every { handle.createQuery(any()) } returns mockk {
                every { bind(0, request.username) } returns this
                every { mapTo(String::class.java) } returns mockk {
                    every { first() } returns null
                }
            }
            
            val capturedPasswordHash = slot<String>()
            every { handle.createUpdate(any()) } returns mockk {
                every { bind(any<Int>(), any()) } returns this
                every { bind(3, capture(capturedPasswordHash)) } returns this
                every { execute() } returns 1
            }
            
            every { jwtService.generateToken(any()) } returns "test-token"

            // When
            authService.register(request)

            // Then
            assertTrue(capturedPasswordHash.isCaptured)
            val hash = capturedPasswordHash.captured
            assertTrue(BCrypt.checkpw("mysecretpassword", hash), "Password should be properly hashed")
        }
    }

    @Nested
    inner class LoginTests {

        @Test
        fun `should login with valid credentials`() {
            // Given
            val request = LoginRequest(
                username = "testuser",
                password = "correctpassword"
            )
            
            val userId = UUID.randomUUID()
            val passwordHash = BCrypt.hashpw("correctpassword", BCrypt.gensalt())
            val user = User(
                id = userId,
                username = "testuser",
                email = "test@example.com",
                passwordHash = passwordHash,
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
                isAdmin = false
            )
            
            every { jdbi.withHandle<User?, Exception>(any()) } answers {
                val callback = firstArg<(Handle) -> User?>()
                callback(handle)
            }
            
            every { handle.createQuery(any()) } returns mockk {
                every { bind(0, request.username) } returns this
                every { map(any<(org.jdbi.v3.core.result.RowView) -> User>()) } returns mockk {
                    every { firstOrNull() } returns user
                }
            }
            
            every { jwtService.generateToken(user) } returns "jwt-token-123"

            // When
            val result = authService.login(request)

            // Then
            assertTrue(result.isSuccess)
            val loginResponse = result.getOrNull()
            assertNotNull(loginResponse)
            assertEquals("jwt-token-123", loginResponse.token)
            assertEquals("testuser", loginResponse.user.username)
        }

        @Test
        fun `should fail login with non-existent user`() {
            // Given
            val request = LoginRequest(
                username = "nonexistent",
                password = "password"
            )
            
            every { jdbi.withHandle<User?, Exception>(any()) } answers {
                val callback = firstArg<(Handle) -> User?>()
                callback(handle)
            }
            
            every { handle.createQuery(any()) } returns mockk {
                every { bind(0, request.username) } returns this
                every { map(any<(org.jdbi.v3.core.result.RowView) -> User>()) } returns mockk {
                    every { firstOrNull() } returns null
                }
            }

            // When
            val result = authService.login(request)

            // Then
            assertTrue(result.isFailure)
            val exception = result.exceptionOrNull()
            assertNotNull(exception)
            assertTrue(exception is IllegalArgumentException)
        }

        @Test
        fun `should fail login with wrong password`() {
            // Given
            val request = LoginRequest(
                username = "testuser",
                password = "wrongpassword"
            )
            
            val userId = UUID.randomUUID()
            val passwordHash = BCrypt.hashpw("correctpassword", BCrypt.gensalt())
            val user = User(
                id = userId,
                username = "testuser",
                email = "test@example.com",
                passwordHash = passwordHash,
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
                isAdmin = false
            )
            
            every { jdbi.withHandle<User?, Exception>(any()) } answers {
                val callback = firstArg<(Handle) -> User?>()
                callback(handle)
            }
            
            every { handle.createQuery(any()) } returns mockk {
                every { bind(0, request.username) } returns this
                every { map(any<(org.jdbi.v3.core.result.RowView) -> User>()) } returns mockk {
                    every { firstOrNull() } returns user
                }
            }

            // When
            val result = authService.login(request)

            // Then
            assertTrue(result.isFailure)
            val exception = result.exceptionOrNull()
            assertNotNull(exception)
            assertTrue(exception is IllegalArgumentException)
            assertTrue(exception.message?.contains("Invalid") == true)
        }
    }

    @Nested
    inner class GetUserByIdTests {

        @Test
        fun `should get user by id`() {
            // Given
            val userId = UUID.randomUUID()
            val user = User(
                id = userId,
                username = "testuser",
                email = "test@example.com",
                passwordHash = "hash",
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
                isAdmin = false
            )
            
            every { jdbi.withHandle<User?, Exception>(any()) } answers {
                val callback = firstArg<(Handle) -> User?>()
                callback(handle)
            }
            
            every { handle.createQuery(any()) } returns mockk {
                every { bind(0, userId.toString()) } returns this
                every { map(any<(org.jdbi.v3.core.result.RowView) -> User>()) } returns mockk {
                    every { firstOrNull() } returns user
                }
            }

            // When
            val result = authService.getUserById(userId)

            // Then
            assertNotNull(result)
            assertEquals(userId, result.id)
            assertEquals("testuser", result.username)
        }

        @Test
        fun `should return null for non-existent user`() {
            // Given
            val userId = UUID.randomUUID()
            
            every { jdbi.withHandle<User?, Exception>(any()) } answers {
                val callback = firstArg<(Handle) -> User?>()
                callback(handle)
            }
            
            every { handle.createQuery(any()) } returns mockk {
                every { bind(0, userId.toString()) } returns this
                every { map(any<(org.jdbi.v3.core.result.RowView) -> User>()) } returns mockk {
                    every { firstOrNull() } returns null
                }
            }

            // When
            val result = authService.getUserById(userId)

            // Then
            assertEquals(null, result)
        }
    }
}
