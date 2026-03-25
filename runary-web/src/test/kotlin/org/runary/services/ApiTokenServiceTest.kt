package org.runary.services

import org.runary.TestFixture
import org.runary.models.CreateUserRequest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ApiTokenServiceTest {
    private lateinit var apiTokenService: ApiTokenService
    private lateinit var authService: AuthService
    private lateinit var jwtService: JwtService
    private lateinit var userId: UUID

    @BeforeEach
    fun setup() {
        val jdbi = TestFixture.database.getJdbi()
        jwtService = JwtService(TestFixture.config.security)
        authService = AuthService(jdbi, jwtService)
        apiTokenService = ApiTokenService(jdbi)

        val result =
            authService.register(
                CreateUserRequest("apitoken_${System.nanoTime()}", "apitoken_${System.nanoTime()}@test.com", org.runary.TestPasswords.DEFAULT),
            )
        userId = jwtService.extractUserId(result.getOrThrow().token)!!
    }

    @Test
    fun `createToken returns token with name and raw token`() {
        val response = apiTokenService.createToken(userId, "My Token")
        assertEquals("My Token", response.name)
        assertTrue(response.token.startsWith("bt_"))
        assertTrue(response.id.isNotBlank())
        assertTrue(response.createdAt.isNotBlank())
    }

    @Test
    fun `listTokens returns created token`() {
        apiTokenService.createToken(userId, "List Test")
        val tokens = apiTokenService.listTokens(userId)
        assertTrue(tokens.any { it.name == "List Test" })
    }

    @Test
    fun `listTokens returns empty for user with no tokens`() {
        val otherResult =
            authService.register(
                CreateUserRequest("notoken_${System.nanoTime()}", "notoken_${System.nanoTime()}@test.com", org.runary.TestPasswords.DEFAULT),
            )
        val otherId = jwtService.extractUserId(otherResult.getOrThrow().token)!!
        val tokens = apiTokenService.listTokens(otherId)
        assertTrue(tokens.isEmpty())
    }

    @Test
    fun `validateToken returns userId for valid token`() {
        val response = apiTokenService.createToken(userId, "Validate Test")
        val resolved = apiTokenService.validateToken(response.token)
        assertEquals(userId, resolved)
    }

    @Test
    fun `validateToken returns null for invalid token`() {
        val resolved = apiTokenService.validateToken("bt_invalid_garbage")
        assertNull(resolved)
    }

    @Test
    fun `revokeToken removes token so validation fails`() {
        val response = apiTokenService.createToken(userId, "Revoke Test")
        val tokenId = UUID.fromString(response.id)
        assertTrue(apiTokenService.revokeToken(userId, tokenId))
        assertNull(apiTokenService.validateToken(response.token))
    }

    @Test
    fun `revokeToken returns false for non-owned token`() {
        val otherResult =
            authService.register(
                CreateUserRequest("revother_${System.nanoTime()}", "revother_${System.nanoTime()}@test.com", org.runary.TestPasswords.DEFAULT),
            )
        val otherId = jwtService.extractUserId(otherResult.getOrThrow().token)!!
        val response = apiTokenService.createToken(userId, "Not Yours")
        val tokenId = UUID.fromString(response.id)
        assertFalse(apiTokenService.revokeToken(otherId, tokenId))
    }

    @Test
    fun `listTokens does not return another user's tokens`() {
        val otherResult =
            authService.register(
                CreateUserRequest("isolate_${System.nanoTime()}", "isolate_${System.nanoTime()}@test.com", org.runary.TestPasswords.DEFAULT),
            )
        val otherId = jwtService.extractUserId(otherResult.getOrThrow().token)!!
        apiTokenService.createToken(userId, "Owner Token")
        val tokens = apiTokenService.listTokens(otherId)
        assertTrue(tokens.none { it.name == "Owner Token" })
    }

    @Test
    fun `multiple tokens can be created for same user`() {
        apiTokenService.createToken(userId, "Token A")
        apiTokenService.createToken(userId, "Token B")
        val tokens = apiTokenService.listTokens(userId)
        val names = tokens.map { it.name }
        assertTrue(names.contains("Token A"))
        assertTrue(names.contains("Token B"))
    }
}
