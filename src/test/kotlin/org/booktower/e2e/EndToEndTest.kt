package org.booktower.e2e

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.http4k.client.JavaHttpClient
import org.http4k.core.*
import org.http4k.core.cookie.Cookie
import org.slf4j.LoggerFactory
import org.booktower.models.LoginResponse
import org.booktower.models.ErrorResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration

private val logger = LoggerFactory.getLogger("booktower.e2e.EndToEndTest")
private val objectMapper = ObjectMapper()

private val BASE_URL = "http://localhost:9999"
private val TEST_TIMEOUT = Duration.ofSeconds(60)

/**
 * End-to-End tests for BookTower API
 * 
 * These tests require a running BookTower server on localhost:9999
 * Run with: mvn test -Dtest=EndToEndTest
 * Or: ./mvnw test -Dtest=EndToEndTest (with Maven wrapper)
 */
class EndToEndTest {
    private val client = JavaHttpClient()

    @Test
    fun `Should register new user with JSON`() {
        logger.info("Testing user registration with JSON...")

        val uniqueUsername = "testuser_${System.currentTimeMillis()}"
        val requestBody = mapOf(
            "username" to uniqueUsername,
            "email" to "$uniqueUsername@example.com",
            "password" to "TestPass123!"
        )

        val response =
            client(
                Request(Method.POST, "$BASE_URL/auth/register")
                    .header("Content-Type", "application/json")
                    .body(objectMapper.writeValueAsString(requestBody))
            )

        assertEquals(Status.CREATED, response.status, "User registration should return 201")
        
        val responseBody = response.bodyString()
        assertTrue(responseBody.contains("token"), "Response should contain token")
        assertTrue(responseBody.contains("user"), "Response should contain user info")
        assertTrue(responseBody.contains(uniqueUsername), "Response should contain username")

        // Check that auth cookie was set
        val setCookie = response.headers.find { it.name == "Set-Cookie" }
        assertNotNull(setCookie, "Response should set authentication cookie")
        assertTrue(setCookie.value.contains("token="), "Cookie should contain token")

        logger.info("✓ User registration with JSON successful")
    }

    @Test
    fun `Should login with valid credentials and JSON`() {
        logger.info("Testing user login with JSON...")

        // First register a user
        val uniqueUsername = "logintest_${System.currentTimeMillis()}"
        registerUser(uniqueUsername, "$uniqueUsername@example.com", "TestPass123!")

        // Then login
        val loginBody = mapOf(
            "username" to uniqueUsername,
            "password" to "TestPass123!"
        )

        val response =
            client(
                Request(Method.POST, "$BASE_URL/auth/login")
                    .header("Content-Type", "application/json")
                    .body(objectMapper.writeValueAsString(loginBody))
            )

        assertEquals(Status.OK, response.status, "Login should return 200")
        
        val loginResponse: LoginResponse = objectMapper.readValue(response.bodyString())
        assertNotNull(loginResponse.token, "Response should contain token")
        assertNotNull(loginResponse.user, "Response should contain user")
        assertEquals(uniqueUsername, loginResponse.user.username, "Username should match")

        // Check that auth cookie was set
        val setCookie = response.headers.find { it.name == "Set-Cookie" }
        assertNotNull(setCookie, "Response should set authentication cookie")

        logger.info("✓ User login with JSON successful")
    }

    @Test
    fun `Should fail registration with invalid email`() {
        logger.info("Testing registration validation...")

        val requestBody = mapOf(
            "username" to "testuser",
            "email" to "invalid-email",
            "password" to "TestPass123!"
        )

        val response =
            client(
                Request(Method.POST, "$BASE_URL/auth/register")
                    .header("Content-Type", "application/json")
                    .body(objectMapper.writeValueAsString(requestBody))
            )

        assertEquals(Status.BAD_REQUEST, response.status, "Invalid email should return 400")
        
        val errorResponse: ErrorResponse = objectMapper.readValue(response.bodyString())
        assertEquals("VALIDATION_ERROR", errorResponse.error, "Should return validation error")

        logger.info("✓ Registration validation working correctly")
    }

    @Test
    fun `Should fail registration with short password`() {
        logger.info("Testing password validation...")

        val uniqueUsername = "shortpass_${System.currentTimeMillis()}"
        val requestBody = mapOf(
            "username" to uniqueUsername,
            "email" to "$uniqueUsername@example.com",
            "password" to "short"
        )

        val response =
            client(
                Request(Method.POST, "$BASE_URL/auth/register")
                    .header("Content-Type", "application/json")
                    .body(objectMapper.writeValueAsString(requestBody))
            )

        assertEquals(Status.BAD_REQUEST, response.status, "Short password should return 400")

        logger.info("✓ Password validation working correctly")
    }

    @Test
    fun `Should fail on duplicate username`() {
        logger.info("Testing duplicate user registration...")

        val uniqueUsername = "duplicate_${System.currentTimeMillis()}"

        // First registration
        val response1 = registerUser(uniqueUsername, "$uniqueUsername@example.com", "TestPass123!")
        assertEquals(Status.CREATED, response1.status, "First registration should succeed")

        // Second registration with same username
        val requestBody = mapOf(
            "username" to uniqueUsername,
            "email" to "different@example.com",
            "password" to "TestPass123!"
        )

        val response2 =
            client(
                Request(Method.POST, "$BASE_URL/auth/register")
                    .header("Content-Type", "application/json")
                    .body(objectMapper.writeValueAsString(requestBody))
            )

        assertEquals(Status.CONFLICT, response2.status, "Duplicate registration should return 409")
        
        val errorResponse: ErrorResponse = objectMapper.readValue(response2.bodyString())
        assertEquals("USER_EXISTS", errorResponse.error, "Should return user exists error")

        logger.info("✓ Duplicate user validation working correctly")
    }

    @Test
    fun `Should fail on invalid credentials`() {
        logger.info("Testing invalid login...")

        val loginBody = mapOf(
            "username" to "nonexistent_${System.currentTimeMillis()}",
            "password" to "wrongpassword"
        )

        val response =
            client(
                Request(Method.POST, "$BASE_URL/auth/login")
                    .header("Content-Type", "application/json")
                    .body(objectMapper.writeValueAsString(loginBody))
            )

        assertEquals(Status.UNAUTHORIZED, response.status, "Invalid login should return 401")
        
        val errorResponse: ErrorResponse = objectMapper.readValue(response.bodyString())
        assertEquals("INVALID_CREDENTIALS", errorResponse.error, "Should return invalid credentials error")

        logger.info("✓ Invalid credentials validation working correctly")
    }

    @Test
    fun `Should fail on empty request body`() {
        logger.info("Testing empty request body...")

        val response =
            client(
                Request(Method.POST, "$BASE_URL/auth/login")
                    .header("Content-Type", "application/json")
                    .body("")
            )

        assertEquals(Status.BAD_REQUEST, response.status, "Empty body should return 400")

        logger.info("✓ Empty request body validation working correctly")
    }

    @Test
    fun `Should logout and clear cookie`() {
        logger.info("Testing user logout...")

        val uniqueUsername = "logouttest_${System.currentTimeMillis()}"
        val token = registerAndGetToken(uniqueUsername, "$uniqueUsername@example.com", "TestPass123!")

        val response =
            client(
                Request(Method.POST, "$BASE_URL/auth/logout")
                    .header("Cookie", "token=$token")
            )

        assertEquals(Status.OK, response.status, "Logout should return 200")

        // Check that cookie was cleared
        val setCookie = response.headers.find { it.name == "Set-Cookie" }
        assertNotNull(setCookie, "Response should set cookie")
        assertTrue(setCookie.value.contains("token="), "Cookie should be present")
        assertTrue(setCookie.value.contains("Max-Age=0") || setCookie.value.contains("Expires="), "Cookie should be expired")

        logger.info("✓ User logout successful")
    }

    @Test
    fun `Should access home page`() {
        logger.info("Testing home page...")

        val response =
            client(
                Request(Method.GET, "$BASE_URL/")
                    .header("Accept", "text/html")
            )

        assertEquals(Status.OK, response.status, "Home page should return 200")
        
        val body = response.bodyString()
        assertTrue(
            body.contains("<html>") || body.contains("<!DOCTYPE html>") || body.contains("BookTower"),
            "Response should be HTML or contain BookTower"
        )

        logger.info("✓ Home page accessible")
    }

    @Test
    fun `Should return login page for unauthenticated users`() {
        logger.info("Testing login page...")

        val response =
            client(
                Request(Method.GET, "$BASE_URL/login")
                    .header("Accept", "text/html")
            )

        assertEquals(Status.OK, response.status, "Login page should return 200")

        logger.info("✓ Login page accessible")
    }

    @Test
    fun `Should return register page`() {
        logger.info("Testing register page...")

        val response =
            client(
                Request(Method.GET, "$BASE_URL/register")
                    .header("Accept", "text/html")
            )

        assertEquals(Status.OK, response.status, "Register page should return 200")

        logger.info("✓ Register page accessible")
    }

    // Helper methods

    private fun registerUser(username: String, email: String, password: String): Response {
        val requestBody = mapOf(
            "username" to username,
            "email" to email,
            "password" to password
        )

        return client(
            Request(Method.POST, "$BASE_URL/auth/register")
                .header("Content-Type", "application/json")
                .body(objectMapper.writeValueAsString(requestBody))
        )
    }

    private fun registerAndGetToken(username: String, email: String, password: String): String {
        val response = registerUser(username, email, password)
        
        if (response.status != Status.CREATED) {
            throw IllegalStateException("Failed to register user: ${response.bodyString()}")
        }

        val setCookie = response.headers.find { it.name == "Set-Cookie" }
            ?: throw IllegalStateException("No Set-Cookie header in response")
        
        return setCookie.value.split(";").first().split("=").getOrNull(1)
            ?: throw IllegalStateException("Could not extract token from cookie")
    }
}
