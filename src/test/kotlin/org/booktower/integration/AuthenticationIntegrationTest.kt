package org.booktower.integration

import org.booktower.config.Database
import org.booktower.config.DatabaseConfig
import org.booktower.handlers.AuthHandler2
import org.booktower.models.CreateUserRequest
import org.booktower.models.LoginRequest
import org.booktower.services.AuthService
import org.booktower.services.JwtService
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import kotlin.test.assertTrue

/**
 * Integration tests for Authentication flow
 * 
 * These tests use an in-memory H2 database to test the full authentication flow
 * from HTTP request through service layer to database.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthenticationIntegrationTest {
    private lateinit var database: Database
    private lateinit var authService: AuthService
    private lateinit var jwtService: JwtService
    private lateinit var authHandler: AuthHandler2

    @BeforeAll
    fun setUp() {
        // Setup in-memory H2 database
        val config = DatabaseConfig(
            url = "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=MySQL",
            username = "sa",
            password = "",
            driver = "org.h2.Driver"
        )
        
        database = Database.connect(config)
        
        // Run migrations
        org.flywaydb.core.Flyway.configure()
            .dataSource(config.url, config.username, config.password)
            .locations("classpath:db/migration")
            .load()
            .migrate()
        
        jwtService = JwtService("test-secret-key-that-is-long-enough-for-hs256", "test-issuer")
        authService = AuthService(database.getJdbi(), jwtService)
        authHandler = AuthHandler2(authService)
    }

    @AfterAll
    fun tearDown() {
        // Cleanup
    }

    @BeforeEach
    fun cleanDatabase() {
        // Clean users table before each test
        database.getJdbi().useHandle<Exception> { handle ->
            handle.execute("DELETE FROM users WHERE username LIKE 'int_test_%'")
        }
    }

    @Test
    fun `complete authentication flow - register, login, logout`() {
        // Step 1: Register a new user
        val uniqueId = System.currentTimeMillis()
        val registerRequest = Request(Method.POST, "/auth/register")
            .header("Content-Type", "application/json")
            .body("""{
                "username": "int_test_user_$uniqueId",
                "email": "int_test_$uniqueId@example.com",
                "password": "TestPassword123!"
            }""".trimIndent())

        val registerResponse = authHandler.register(registerRequest)
        assertEquals(Status.CREATED, registerResponse.status, "Registration should succeed")
        
        val setCookie = registerResponse.headers.find { it.name == "Set-Cookie" }
        assertNotNull(setCookie, "Registration should set auth cookie")
        val authToken = setCookie.value.split(";").first().split("=").getOrNull(1)
        assertNotNull(authToken, "Should have JWT token in cookie")

        // Step 2: Login with the same credentials
        val loginRequest = Request(Method.POST, "/auth/login")
            .header("Content-Type", "application/json")
            .body("""{
                "username": "int_test_user_$uniqueId",
                "password": "TestPassword123!"
            }""".trimIndent())

        val loginResponse = authHandler.login(loginRequest)
        assertEquals(Status.OK, loginResponse.status, "Login should succeed")

        // Step 3: Logout
        val logoutRequest = Request(Method.POST, "/auth/logout")
            .header("Cookie", "token=$authToken")

        val logoutResponse = authHandler.logout(logoutRequest)
        assertEquals(Status.OK, logoutResponse.status, "Logout should succeed")
        
        val clearCookie = logoutResponse.headers.find { it.name == "Set-Cookie" }
        assertNotNull(clearCookie, "Logout should clear cookie")
        assertTrue(clearCookie.value.contains("Max-Age=0"), "Cookie should be expired")
    }

    @Test
    fun `should fail to register duplicate users`() {
        val uniqueId = System.currentTimeMillis()
        val username = "int_test_dup_$uniqueId"

        // First registration
        val registerRequest1 = Request(Method.POST, "/auth/register")
            .header("Content-Type", "application/json")
            .body("""{
                "username": "$username",
                "email": "dup1_$uniqueId@example.com",
                "password": "TestPassword123!"
            }""".trimIndent())

        val response1 = authHandler.register(registerRequest1)
        assertEquals(Status.CREATED, response1.status, "First registration should succeed")

        // Second registration with same username
        val registerRequest2 = Request(Method.POST, "/auth/register")
            .header("Content-Type", "application/json")
            .body("""{
                "username": "$username",
                "email": "dup2_$uniqueId@example.com",
                "password": "TestPassword123!"
            }""".trimIndent())

        val response2 = authHandler.register(registerRequest2)
        assertEquals(Status.CONFLICT, response2.status, "Duplicate registration should fail")
    }

    @Test
    fun `should fail login with wrong password`() {
        val uniqueId = System.currentTimeMillis()
        val username = "int_test_wrong_$uniqueId"

        // Register user
        val registerRequest = Request(Method.POST, "/auth/register")
            .header("Content-Type", "application/json")
            .body("""{
                "username": "$username",
                "email": "wrong_$uniqueId@example.com",
                "password": "CorrectPassword123!"
            }""".trimIndent())

        val registerResponse = authHandler.register(registerRequest)
        assertEquals(Status.CREATED, registerResponse.status)

        // Try to login with wrong password
        val loginRequest = Request(Method.POST, "/auth/login")
            .header("Content-Type", "application/json")
            .body("""{
                "username": "$username",
                "password": "WrongPassword123!"
            }""".trimIndent())

        val loginResponse = authHandler.login(loginRequest)
        assertEquals(Status.UNAUTHORIZED, loginResponse.status, "Login with wrong password should fail")
    }

    @Test
    fun `should validate input on registration`() {
        // Test with invalid email
        val invalidEmailRequest = Request(Method.POST, "/auth/register")
            .header("Content-Type", "application/json")
            .body("""{
                "username": "testuser",
                "email": "not-an-email",
                "password": "password123"
            }""".trimIndent())

        val response = authHandler.register(invalidEmailRequest)
        assertEquals(Status.BAD_REQUEST, response.status, "Invalid email should fail")
    }

    @Test
    fun `should validate password length`() {
        val shortPasswordRequest = Request(Method.POST, "/auth/register")
            .header("Content-Type", "application/json")
            .body("""{
                "username": "testuser_${System.currentTimeMillis()}",
                "email": "test@test.com",
                "password": "short"
            }""".trimIndent())

        val response = authHandler.register(shortPasswordRequest)
        assertEquals(Status.BAD_REQUEST, response.status, "Short password should fail")
    }
}
