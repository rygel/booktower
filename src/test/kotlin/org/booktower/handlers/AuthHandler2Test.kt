package org.booktower.handlers

import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.*
import org.booktower.models.*
import org.booktower.services.AuthService
import org.http4k.core.*
import org.http4k.core.cookie.Cookie
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.util.*
import kotlin.Result

/**
 * Unit tests for AuthHandler2
 */
class AuthHandler2Test {
    private lateinit var authService: AuthService
    private lateinit var authHandler: AuthHandler2
    private val objectMapper = ObjectMapper()

    @BeforeEach
    fun setUp() {
        authService = mockk()
        authHandler = AuthHandler2(authService)
    }

    @Nested
    inner class RegisterTests {

        @Test
        fun `should register user with valid JSON`() {
            // Given
            val requestBody = CreateUserRequest(
                username = "testuser",
                email = "test@example.com",
                password = "password123"
            )
            val loginResponse = LoginResponse(
                token = "jwt-token",
                user = UserDto(
                    id = UUID.randomUUID().toString(),
                    username = "testuser",
                    email = "test@example.com",
                    createdAt = java.time.Instant.now().toString(),
                    isAdmin = false
                )
            )

            every { authService.register(requestBody) } returns Result.success(loginResponse)

            val request = Request(Method.POST, "/auth/register")
                .header("Content-Type", "application/json")
                .body(objectMapper.writeValueAsString(requestBody))

            // When
            val response = authHandler.register(request)

            // Then
            assertEquals(Status.CREATED, response.status)
            assertTrue(response.bodyString().contains("token"))
            
            val setCookie = response.headers.find { it.name == "Set-Cookie" }
            assertNotNull(setCookie)
            assertTrue(setCookie.value.contains("token=jwt-token"))
        }

        @Test
        fun `should return 400 for empty request body`() {
            // Given
            val request = Request(Method.POST, "/auth/register")
                .header("Content-Type", "application/json")
                .body("")

            // When
            val response = authHandler.register(request)

            // Then
            assertEquals(Status.BAD_REQUEST, response.status)
            val error = objectMapper.readValue(response.bodyString(), ErrorResponse::class.java)
            assertEquals("VALIDATION_ERROR", error.error)
        }

        @Test
        fun `should return 400 for invalid username`() {
            // Given
            val requestBody = """{"username": "ab", "email": "test@test.com", "password": "password123"}"""
            val request = Request(Method.POST, "/auth/register")
                .header("Content-Type", "application/json")
                .body(requestBody)

            // When
            val response = authHandler.register(request)

            // Then
            assertEquals(Status.BAD_REQUEST, response.status)
            val error = objectMapper.readValue(response.bodyString(), ErrorResponse::class.java)
            assertEquals("VALIDATION_ERROR", error.error)
            assertTrue(error.message.contains("Username"))
        }

        @Test
        fun `should return 400 for invalid email`() {
            // Given
            val requestBody = """{"username": "testuser", "email": "invalid-email", "password": "password123"}"""
            val request = Request(Method.POST, "/auth/register")
                .header("Content-Type", "application/json")
                .body(requestBody)

            // When
            val response = authHandler.register(request)

            // Then
            assertEquals(Status.BAD_REQUEST, response.status)
            val error = objectMapper.readValue(response.bodyString(), ErrorResponse::class.java)
            assertEquals("VALIDATION_ERROR", error.error)
            assertTrue(error.message.contains("email"))
        }

        @Test
        fun `should return 400 for short password`() {
            // Given
            val requestBody = """{"username": "testuser", "email": "test@test.com", "password": "short"}"""
            val request = Request(Method.POST, "/auth/register")
                .header("Content-Type", "application/json")
                .body(requestBody)

            // When
            val response = authHandler.register(request)

            // Then
            assertEquals(Status.BAD_REQUEST, response.status)
            val error = objectMapper.readValue(response.bodyString(), ErrorResponse::class.java)
            assertEquals("VALIDATION_ERROR", error.error)
            assertTrue(error.message.contains("Password"))
        }

        @Test
        fun `should return 409 for duplicate user`() {
            // Given
            val requestBody = CreateUserRequest(
                username = "existinguser",
                email = "test@example.com",
                password = "password123"
            )

            every { authService.register(requestBody) } returns 
                Result.failure(IllegalArgumentException("Username already exists"))

            val request = Request(Method.POST, "/auth/register")
                .header("Content-Type", "application/json")
                .body(objectMapper.writeValueAsString(requestBody))

            // When
            val response = authHandler.register(request)

            // Then
            assertEquals(Status.CONFLICT, response.status)
            val error = objectMapper.readValue(response.bodyString(), ErrorResponse::class.java)
            assertEquals("USER_EXISTS", error.error)
        }

        @Test
        fun `should return 500 for unexpected error`() {
            // Given
            val requestBody = CreateUserRequest(
                username = "testuser",
                email = "test@example.com",
                password = "password123"
            )

            every { authService.register(requestBody) } returns 
                Result.failure(RuntimeException("Database error"))

            val request = Request(Method.POST, "/auth/register")
                .header("Content-Type", "application/json")
                .body(objectMapper.writeValueAsString(requestBody))

            // When
            val response = authHandler.register(request)

            // Then
            assertEquals(Status.INTERNAL_SERVER_ERROR, response.status)
            val error = objectMapper.readValue(response.bodyString(), ErrorResponse::class.java)
            assertEquals("INTERNAL_ERROR", error.error)
        }
    }

    @Nested
    inner class LoginTests {

        @Test
        fun `should login with valid credentials`() {
            // Given
            val requestBody = LoginRequest(
                username = "testuser",
                password = "password123"
            )
            val loginResponse = LoginResponse(
                token = "jwt-token",
                user = UserDto(
                    id = UUID.randomUUID().toString(),
                    username = "testuser",
                    email = "test@example.com",
                    createdAt = java.time.Instant.now().toString(),
                    isAdmin = false
                )
            )

            every { authService.login(requestBody) } returns Result.success(loginResponse)

            val request = Request(Method.POST, "/auth/login")
                .header("Content-Type", "application/json")
                .body(objectMapper.writeValueAsString(requestBody))

            // When
            val response = authHandler.login(request)

            // Then
            assertEquals(Status.OK, response.status)
            assertTrue(response.bodyString().contains("token"))
            
            val setCookie = response.headers.find { it.name == "Set-Cookie" }
            assertNotNull(setCookie)
        }

        @Test
        fun `should return 400 for empty request body`() {
            // Given
            val request = Request(Method.POST, "/auth/login")
                .header("Content-Type", "application/json")
                .body("")

            // When
            val response = authHandler.login(request)

            // Then
            assertEquals(Status.BAD_REQUEST, response.status)
        }

        @Test
        fun `should return 400 for missing username`() {
            // Given
            val requestBody = """{"username": "", "password": "password123"}"""
            val request = Request(Method.POST, "/auth/login")
                .header("Content-Type", "application/json")
                .body(requestBody)

            // When
            val response = authHandler.login(request)

            // Then
            assertEquals(Status.BAD_REQUEST, response.status)
        }

        @Test
        fun `should return 401 for invalid credentials`() {
            // Given
            val requestBody = LoginRequest(
                username = "testuser",
                password = "wrongpassword"
            )

            every { authService.login(requestBody) } returns 
                Result.failure(IllegalArgumentException("Invalid username or password"))

            val request = Request(Method.POST, "/auth/login")
                .header("Content-Type", "application/json")
                .body(objectMapper.writeValueAsString(requestBody))

            // When
            val response = authHandler.login(request)

            // Then
            assertEquals(Status.UNAUTHORIZED, response.status)
            val error = objectMapper.readValue(response.bodyString(), ErrorResponse::class.java)
            assertEquals("INVALID_CREDENTIALS", error.error)
        }
    }

    @Nested
    inner class LogoutTests {

        @Test
        fun `should logout and clear cookie`() {
            // Given
            val request = Request(Method.POST, "/auth/logout")
                .header("Cookie", "token=some-token")

            // When
            val response = authHandler.logout(request)

            // Then
            assertEquals(Status.OK, response.status)
            
            val setCookie = response.headers.find { it.name == "Set-Cookie" }
            assertNotNull(setCookie)
            assertTrue(setCookie.value.contains("Max-Age=0") || setCookie.value.contains("token="))
        }
    }
}
