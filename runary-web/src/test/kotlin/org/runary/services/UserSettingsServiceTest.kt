package org.runary.services

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.runary.TestFixture
import org.runary.models.CreateUserRequest
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UserSettingsServiceTest {
    private lateinit var userSettingsService: UserSettingsService
    private lateinit var authService: AuthService
    private lateinit var jwtService: JwtService
    private lateinit var userId: UUID

    @BeforeEach
    fun setup() {
        val jdbi = TestFixture.database.getJdbi()
        jwtService = JwtService(TestFixture.config.security)
        authService = AuthService(jdbi, jwtService)
        userSettingsService = UserSettingsService(jdbi)

        val result =
            authService.register(
                CreateUserRequest("settings_${System.nanoTime()}", "settings_${System.nanoTime()}@test.com", org.runary.TestPasswords.DEFAULT),
            )
        userId = jwtService.extractUserId(result.getOrThrow().token)!!
    }

    @Test
    fun `set and get a setting`() {
        userSettingsService.set(userId, "theme", "dark")
        val value = userSettingsService.get(userId, "theme")
        assertEquals("dark", value)
    }

    @Test
    fun `get returns null for missing key`() {
        val value = userSettingsService.get(userId, "nonexistent")
        assertNull(value)
    }

    @Test
    fun `set overwrites existing value`() {
        userSettingsService.set(userId, "lang", "en")
        userSettingsService.set(userId, "lang", "fr")
        assertEquals("fr", userSettingsService.get(userId, "lang"))
    }

    @Test
    fun `getAll returns all settings for user`() {
        userSettingsService.set(userId, "key1", "val1")
        userSettingsService.set(userId, "key2", "val2")
        val all = userSettingsService.getAll(userId)
        assertEquals("val1", all["key1"])
        assertEquals("val2", all["key2"])
    }

    @Test
    fun `getAll returns empty map for user with no settings`() {
        val otherResult =
            authService.register(
                CreateUserRequest("emptyset_${System.nanoTime()}", "emptyset_${System.nanoTime()}@test.com", org.runary.TestPasswords.DEFAULT),
            )
        val otherId = jwtService.extractUserId(otherResult.getOrThrow().token)!!
        assertTrue(userSettingsService.getAll(otherId).isEmpty())
    }

    @Test
    fun `delete removes setting`() {
        userSettingsService.set(userId, "deleteme", "value")
        val deleted = userSettingsService.delete(userId, "deleteme")
        assertTrue(deleted)
        assertNull(userSettingsService.get(userId, "deleteme"))
    }

    @Test
    fun `delete returns false for non-existent key`() {
        assertFalse(userSettingsService.delete(userId, "no-such-key"))
    }

    @Test
    fun `set throws for blank key`() {
        assertThrows<IllegalArgumentException> {
            userSettingsService.set(userId, "", "value")
        }
    }

    @Test
    fun `set throws for key with invalid characters`() {
        assertThrows<IllegalArgumentException> {
            userSettingsService.set(userId, "bad key!", "value")
        }
    }

    @Test
    fun `set throws for key longer than 50 characters`() {
        val longKey = "a".repeat(51)
        assertThrows<IllegalArgumentException> {
            userSettingsService.set(userId, longKey, "value")
        }
    }

    @Test
    fun `settings are isolated between users`() {
        val otherResult =
            authService.register(
                CreateUserRequest("isolated_${System.nanoTime()}", "isolated_${System.nanoTime()}@test.com", org.runary.TestPasswords.DEFAULT),
            )
        val otherId = jwtService.extractUserId(otherResult.getOrThrow().token)!!

        userSettingsService.set(userId, "shared-key", "user1-value")
        userSettingsService.set(otherId, "shared-key", "user2-value")

        assertEquals("user1-value", userSettingsService.get(userId, "shared-key"))
        assertEquals("user2-value", userSettingsService.get(otherId, "shared-key"))
    }
}
