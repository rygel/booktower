package org.runary.services

import org.runary.config.SecurityConfig
import org.runary.models.User
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JwtServiceTest {
    private lateinit var jwtService: JwtService
    private val testUser =
        User(
            id = UUID.randomUUID(),
            username = "testuser",
            email = "test@example.com",
            passwordHash = "hash",
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            isAdmin = false,
        )

    @BeforeEach
    fun setup() {
        val config =
            SecurityConfig(
                jwtSecret = "test-secret-key-not-for-production",
                jwtIssuer = "runary",
                sessionTimeout = 86400,
            )
        jwtService = JwtService(config)
    }

    @Test
    fun `generateToken returns non-empty token`() {
        val token = jwtService.generateToken(testUser)
        assertTrue(token.isNotBlank())
    }

    @Test
    fun `verifyToken validates a generated token`() {
        val token = jwtService.generateToken(testUser)
        val decoded = jwtService.verifyToken(token)
        assertNotNull(decoded)
        assertEquals(testUser.id.toString(), decoded.subject)
    }

    @Test
    fun `verifyToken returns null for invalid token`() {
        val decoded = jwtService.verifyToken("invalid.token.here")
        assertNull(decoded)
    }

    @Test
    fun `extractUserId returns correct UUID from token`() {
        val token = jwtService.generateToken(testUser)
        val userId = jwtService.extractUserId(token)
        assertEquals(testUser.id, userId)
    }

    @Test
    fun `extractUserId returns null for invalid token`() {
        val userId = jwtService.extractUserId("bad-token")
        assertNull(userId)
    }

    @Test
    fun `token contains expected claims`() {
        val token = jwtService.generateToken(testUser)
        val decoded = jwtService.verifyToken(token)!!
        assertEquals(testUser.username, decoded.getClaim("username").asString())
        assertEquals(testUser.email, decoded.getClaim("email").asString())
        assertEquals(testUser.isAdmin, decoded.getClaim("admin").asBoolean())
    }

    @Test
    fun `token from different secret is rejected`() {
        val otherConfig =
            SecurityConfig(
                jwtSecret = "different-secret",
                jwtIssuer = "runary",
                sessionTimeout = 86400,
            )
        val otherService = JwtService(otherConfig)
        val token = otherService.generateToken(testUser)
        val decoded = jwtService.verifyToken(token)
        assertNull(decoded)
    }

    @Test
    fun `admin user token has admin claim true`() {
        val adminUser = testUser.copy(isAdmin = true)
        val token = jwtService.generateToken(adminUser)
        val decoded = jwtService.verifyToken(token)!!
        assertTrue(decoded.getClaim("admin").asBoolean())
    }
}
