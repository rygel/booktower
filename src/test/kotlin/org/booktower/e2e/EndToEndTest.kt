package org.booktower.e2e

import org.http4k.core.*
import org.http4k.core.body.form
import org.http4k.client.JavaHttpClient
import org.slf4j.LoggerFactory
import kotlin.test.Test
import kotlin.time.Duration
import kotlin.time.temporal.TemporalAmount

private val logger = LoggerFactory.getLogger("booktower.e2e.EndToEndTest")

private val BASE_URL = "http://localhost:9999"
private val TEST_TIMEOUT = Duration.ofSeconds(60)

class EndToEndTest {

    private val client = JavaHttpClient()

    @Test
    fun `Should register new user`() {
        logger.info("Testing user registration...")
        
        val response = client(Request(Method.POST, "$BASE_URL/auth/register")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .body(formBody(
                "username" to "testuser",
                "email" to "testuser@example.com",
                "password" to "TestPass123!"
            ))
        )

        assertEquals(Status.CREATED, response.status, "User registration should return 201")
        assertTrue(response.bodyString().contains("user"), "Response should contain user info")
        
        logger.info("✓ User registration successful")
    }

    @Test
    fun `Should login with valid credentials`() {
        logger.info("Testing user login...")
        
        val response = client(Request(Method.POST, "$BASE_URL/auth/login")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .body(formBody(
                "username" to "testuser",
                "password" to "TestPass123!"
            ))
        )

        assertEquals(Status.OK, response.status, "Login should return 200")
        assertTrue(response.bodyString().contains("user") || response.headers.find { it.name == "Set-Cookie" } != null, 
                   "Response should contain user info or set cookie")
        
        logger.info("✓ User login successful")
    }

    @Test
    fun `Should create library`() {
        logger.info("Testing library creation...")
        
        val token = loginAndGetToken()
        val response = client(Request(Method.POST, "$BASE_URL/api/libraries")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Cookie", "token=$token")
            .body(formBody(
                "name" to "Test Library",
                "path" to "/tmp/test-library"
            ))
        )

        assertEquals(Status.CREATED, response.status, "Library creation should return 201")
        assertTrue(response.bodyString().contains("\"name\":\"Test Library\""), 
                   "Response should contain library name")
        
        logger.info("✓ Library creation successful")
    }

    @Test
    fun `Should list libraries`() {
        logger.info("Testing library listing...")
        
        val token = loginAndGetToken()
        val response = client(Request(Method.GET, "$BASE_URL/api/libraries")
            .header("Cookie", "token=$token")
        )

        assertEquals(Status.OK, response.status, "Library listing should return 200")
        assertTrue(response.bodyString().isNotEmpty(), "Response should contain data")
        
        logger.info("✓ Library listing successful")
    }

    @Test
    fun `Should add book to library`() {
        logger.info("Testing book creation...")
        
        val token = loginAndGetToken()
        val response = client(Request(Method.POST, "$BASE_URL/api/books")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Cookie", "token=$token")
            .body(formBody(
                "title" to "Test Book",
                "author" to "Test Author",
                "description" to "A test book",
                "libraryId" to ""
            ))
        )

        assertEquals(Status.CREATED, response.status, "Book creation should return 201")
        assertTrue(response.bodyString().contains("\"title\":\"Test Book\""), 
                   "Response should contain book title")
        
        logger.info("✓ Book creation successful")
    }

    @Test
    fun `Should list books`() {
        logger.info("Testing book listing...")
        
        val token = loginAndGetToken()
        val response = client(Request(Method.GET, "$BASE_URL/api/books")
            .header("Cookie", "token=$token")
        )

        assertEquals(Status.OK, response.status, "Book listing should return 200")
        
        logger.info("✓ Book listing successful")
    }

    @Test
    fun `Should get recent books`() {
        logger.info("Testing recent books...")
        
        val token = loginAndGetToken()
        val response = client(Request(Method.GET, "$BASE_URL/api/recent")
            .header("Cookie", "token=$token")
        )

        assertEquals(Status.OK, response.status, "Recent books should return 200")
        
        logger.info("✓ Recent books retrieval successful")
    }

    @Test
    fun `Should logout`() {
        logger.info("Testing user logout...")
        
        val token = loginAndGetToken()
        val response = client(Request(Method.POST, "$BASE_URL/auth/logout")
            .header("Cookie", "token=$token")
        )

        assertEquals(Status.OK, response.status, "Logout should return 200")
        
        logger.info("✓ User logout successful")
    }

    @Test
    fun `Should fail on duplicate username`() {
        logger.info("Testing duplicate user registration...")
        
        val response1 = client(Request(Method.POST, "$BASE_URL/auth/register")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .body(formBody(
                "username" to "duplicateuser",
                "email" to "duplicate@example.com",
                "password" to "Pass123!"
            ))
        )

        assertEquals(Status.CREATED, response1.status, "First registration should succeed")
        
        val response2 = client(Request(Method.POST, "$BASE_URL/auth/register")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .body(formBody(
                "username" to "duplicateuser",
                "email" to "duplicate2@example.com",
                "password" to "Pass123!"
            ))
        )

        assertEquals(Status.BAD_REQUEST, response2.status, "Duplicate registration should fail")
        
        logger.info("✓ Duplicate user validation working correctly")
    }

    @Test
    fun `Should fail on invalid credentials`() {
        logger.info("Testing invalid login...")
        
        val response = client(Request(Method.POST, "$BASE_URL/auth/login")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .body(formBody(
                "username" to "nonexistent",
                "password" to "wrongpassword"
            ))
        )

        assertEquals(Status.UNAUTHORIZED, response.status, "Invalid login should return 401")
        
        logger.info("✓ Invalid credentials validation working correctly")
    }

    @Test
    fun `Should fail on unauthorized access`() {
        logger.info("Testing unauthorized library access...")
        
        val response = client(Request(Method.GET, "$BASE_URL/api/libraries")
            .header("Cookie", "token=invalid_token")
        )

        assertEquals(Status.UNAUTHORIZED, response.status, "Unauthorized request should return 401")
        
        logger.info("✓ Unauthorized access validation working correctly")
    }

    @Test
    fun `Should access home page`() {
        logger.info("Testing home page...")
        
        val response = client(Request(Method.GET, "$BASE_URL/")
            .header("Accept", "text/html")
        )

        assertEquals(Status.OK, response.status, "Home page should return 200")
        assertTrue(response.bodyString().contains("<html>") || response.bodyString().contains("BookTower"),
                   "Response should be HTML")
        
        logger.info("✓ Home page accessible")
    }

    @Test
    fun `Should handle server errors gracefully`() {
        logger.info("Testing error handling...")
        
        val response = client(Request(Method.GET, "$BASE_URL/api/nonexistent")
            .header("Cookie", "token=valid_token")
        )

        assertTrue(response.status != Status.OK, "Non-existent endpoint should not return 200")
        
        logger.info("✓ Server error handling working correctly")
    }

    private fun loginAndGetToken(): String {
        val loginResponse = client(Request(Method.POST, "$BASE_URL/auth/login")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .body(formBody(
                "username" to "testuser",
                "password" to "TestPass123!"
            ))
        )

        if (loginResponse.status == Status.OK) {
            val setCookie = loginResponse.headers.find { it.name == "Set-Cookie" }
            return setCookie?.value?.split(";")?.first()?.split("=")?.get(1) ?: ""
        }
        
        throw IllegalStateException("Failed to login and get token for test")
    }

    private fun formBody(vararg pairs: Pair<String, String>): String {
        return pairs.joinToString("&") { (k, v) -> "$k=$v" }
    }
}

fun main() {
    logger.info("=".repeat(60, "="))
    logger.info("BookTower End-to-End Test Suite")
    logger.info("=".repeat(60, "="))
    logger.info("Base URL: $BASE_URL")
    logger.info("Make sure BookTower is running on $BASE_URL")
    logger.info("Run with: mvn test -Dtest=EndToEndTest")
    logger.info("=".repeat(60, "="))
}
