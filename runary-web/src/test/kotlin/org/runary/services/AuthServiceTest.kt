package org.runary.services

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.runary.TestFixture
import org.runary.models.CreateUserRequest
import org.runary.models.LoginRequest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AuthServiceTest {
    private lateinit var authService: AuthService
    private lateinit var jwtService: JwtService

    @BeforeEach
    fun setup() {
        jwtService = JwtService(TestFixture.config.security)
        authService = AuthService(TestFixture.database.getJdbi(), jwtService)
    }

    @Test
    fun `register creates user and returns token`() {
        val request =
            CreateUserRequest(
                username = "newuser_${System.nanoTime()}",
                email = "new_${System.nanoTime()}@example.com",
                password = org.runary.TestPasswords.DEFAULT,
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
        authService.register(CreateUserRequest(username, "a_${System.nanoTime()}@test.com", org.runary.TestPasswords.DEFAULT))
        val result = authService.register(CreateUserRequest(username, "b_${System.nanoTime()}@test.com", org.runary.TestPasswords.DEFAULT))
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `login succeeds with correct credentials`() {
        val username = "logintest_${System.nanoTime()}"
        val password = org.runary.TestPasswords.DEFAULT
        authService.register(CreateUserRequest(username, "login_${System.nanoTime()}@test.com", password))
        val result = authService.login(LoginRequest(username, password))
        assertTrue(result.isSuccess)
        assertEquals(username, result.getOrThrow().user.username)
    }

    @Test
    fun `login fails with wrong password`() {
        val username = "wrongpw_${System.nanoTime()}"
        authService.register(CreateUserRequest(username, "wp_${System.nanoTime()}@test.com", org.runary.TestPasswords.DEFAULT))
        assertTrue(authService.login(LoginRequest(username, "wrongpassword")).isFailure)
    }

    @Test
    fun `login fails with non-existent username`() {
        assertTrue(authService.login(LoginRequest("nonexistent_${System.nanoTime()}", org.runary.TestPasswords.DEFAULT)).isFailure)
    }

    @Test
    fun `login succeeds with email address`() {
        val username = "emaillogin_${System.nanoTime()}"
        val email = "$username@test.com"
        val password = org.runary.TestPasswords.DEFAULT
        authService.register(CreateUserRequest(username, email, password))
        val result = authService.login(LoginRequest(email, password))
        assertTrue(result.isSuccess)
        assertEquals(username, result.getOrThrow().user.username)
    }

    @Test
    fun `getUserById returns registered user`() {
        val username = "getbyid_${System.nanoTime()}"
        val registerResult = authService.register(CreateUserRequest(username, "gbi_${System.nanoTime()}@test.com", org.runary.TestPasswords.DEFAULT))
        val userId = jwtService.extractUserId(registerResult.getOrThrow().token)!!
        val user = authService.getUserById(userId)
        assertNotNull(user)
        assertEquals(username, user.username)
    }

    @Test
    fun `generated token is valid`() {
        val result =
            authService.register(
                CreateUserRequest("tokentest_${System.nanoTime()}", "tt_${System.nanoTime()}@test.com", org.runary.TestPasswords.DEFAULT),
            )
        assertNotNull(jwtService.extractUserId(result.getOrThrow().token))
    }
}
