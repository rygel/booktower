package org.booktower.services

import org.booktower.TestFixture
import org.booktower.models.CreateUserRequest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AdminServiceTest {
    private lateinit var adminService: AdminService
    private lateinit var authService: AuthService
    private lateinit var jwtService: JwtService

    @BeforeEach
    fun setup() {
        val jdbi = TestFixture.database.getJdbi()
        jwtService = JwtService(TestFixture.config.security)
        authService = AuthService(jdbi, jwtService)
        adminService = AdminService(jdbi)
    }

    private fun registerUser(prefix: String = "admin"): UUID {
        val result =
            authService.register(
                CreateUserRequest("${prefix}_${System.nanoTime()}", "${prefix}_${System.nanoTime()}@test.com", org.booktower.TestPasswords.DEFAULT),
            )
        return jwtService.extractUserId(result.getOrThrow().token)!!
    }

    @Test
    fun `listUsers returns registered users`() {
        registerUser()
        val users = adminService.listUsers()
        assertTrue(users.isNotEmpty())
    }

    @Test
    fun `listUsers includes username and email`() {
        val nano = System.nanoTime()
        val username = "listcheck_$nano"
        val email = "listcheck_$nano@test.com"
        authService.register(CreateUserRequest(username, email, org.booktower.TestPasswords.DEFAULT))
        val users = adminService.listUsers()
        val match = users.firstOrNull { it.username == username }
        assertNotNull(match)
        assertEquals(email, match.email)
    }

    @Test
    fun `setAdmin grants admin role`() {
        val userId = registerUser()
        val result = adminService.setAdmin(userId, true)
        assertTrue(result)
        val users = adminService.listUsers()
        val user = users.first { it.id == userId.toString() }
        assertTrue(user.isAdmin)
    }

    @Test
    fun `setAdmin revokes admin role`() {
        val userId = registerUser()
        adminService.setAdmin(userId, true)
        adminService.setAdmin(userId, false)
        val users = adminService.listUsers()
        val user = users.first { it.id == userId.toString() }
        assertFalse(user.isAdmin)
    }

    @Test
    fun `setAdmin returns false for non-existent user`() {
        val result = adminService.setAdmin(UUID.randomUUID(), true)
        assertFalse(result)
    }

    @Test
    fun `deleteUser removes the user`() {
        val actorId = registerUser("actor")
        val targetId = registerUser("target")
        val result = adminService.deleteUser(actorId, targetId)
        assertTrue(result)
        val users = adminService.listUsers()
        assertTrue(users.none { it.id == targetId.toString() })
    }

    @Test
    fun `deleteUser returns false for non-existent user`() {
        val actorId = registerUser()
        assertFalse(adminService.deleteUser(actorId, UUID.randomUUID()))
    }

    @Test
    fun `deleteUser throws when actor tries to delete themselves`() {
        val userId = registerUser()
        assertThrows<IllegalArgumentException> {
            adminService.deleteUser(userId, userId)
        }
    }

    @Test
    fun `listUsers isAdmin defaults to false for new users`() {
        val userId = registerUser("notadmin")
        val users = adminService.listUsers()
        val user = users.first { it.id == userId.toString() }
        assertFalse(user.isAdmin)
    }
}
