package org.booktower.services

import org.booktower.config.AppConfig
import org.booktower.config.Database
import org.booktower.models.CreateUserRequest
import org.booktower.models.LoginRequest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AuthServiceTest {
    private lateinit var authService: AuthService
    private lateinit var jwtService: JwtService

    @BeforeEach
    fun setup() {
        val config = AppConfig.load()
        val database = Database.connect(config.database)
        jwtService = JwtService(config.security)
        authService = AuthService(database.getJdbi(), jwtService)
    }

    @Test
    fun `register creates user and returns token`() {
        val request = CreateUserRequest(
            username = "newuser_${System.nanoTime()}",
            email = "new_${System.nanoTime()}@example.com",
            password = "password123",
        )

        val result = authService.register(request)

        assertTrue(result.isSuccess)
        val response = result.getOrThrow()
        assertTrue(response.token.isNotBlank())
        assertEquals(request.username, response.user.username)
        assertEquals(request.email, response.user.email)
    }

    @Test
    fun `register fails for duplicate username`() {
        val username = "duplicate_${System.nanoTime()}"
        val request1 = CreateUserRequest(username, "a_${System.nanoTime()}@test.com", "password123")
        val request2 = CreateUserRequest(username, "b_${System.nanoTime()}@test.com", "password123")

        authService.register(request1)
        assertThrows<IllegalArgumentException> {
            authService.register(request2)
        }
    }

    @Test
    fun `login succeeds with correct credentials`() {
        val username = "logintest_${System.nanoTime()}"
        val password = "password123"
        authService.register(CreateUserRequest(username, "login_${System.nanoTime()}@test.com", password))

        val result = authService.login(LoginRequest(username, password))

        assertTrue(result.isSuccess)
        val response = result.getOrThrow()
        assertTrue(response.token.isNotBlank())
        assertEquals(username, response.user.username)
    }

    @Test
    fun `login fails with wrong password`() {
        val username = "wrongpw_${System.nanoTime()}"
        authService.register(CreateUserRequest(username, "wp_${System.nanoTime()}@test.com", "password123"))

        val result = authService.login(LoginRequest(username, "wrongpassword"))

        assertTrue(result.isFailure)
    }

    @Test
    fun `login fails with non-existent username`() {
        val result = authService.login(LoginRequest("nonexistent_${System.nanoTime()}", "password123"))
        assertTrue(result.isFailure)
    }

    @Test
    fun `getUserById returns registered user`() {
        val username = "getbyid_${System.nanoTime()}"
        val registerResult = authService.register(
            CreateUserRequest(username, "gbi_${System.nanoTime()}@test.com", "password123"),
        )
        val userId = jwtService.extractUserId(registerResult.getOrThrow().token)!!

        val user = authService.getUserById(userId)

        assertNotNull(user)
        assertEquals(username, user.username)
    }

    @Test
    fun `generated token is valid`() {
        val username = "tokentest_${System.nanoTime()}"
        val result = authService.register(
            CreateUserRequest(username, "tt_${System.nanoTime()}@test.com", "password123"),
        )
        val token = result.getOrThrow().token
        val userId = jwtService.extractUserId(token)

        assertNotNull(userId)
    }
}
