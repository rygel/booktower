package org.booktower.services

import org.booktower.models.*
import org.jdbi.v3.core.Jdbi
import org.jdbi.v3.core.result.RowView
import org.mindrot.jbcrypt.BCrypt
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

private val logger = LoggerFactory.getLogger("booktower.AuthService")

class AuthService(
    private val jdbi: Jdbi,
    private val jwtService: JwtService,
) {
    fun register(request: CreateUserRequest): Result<LoginResponse> {
        val now = Instant.now()
        val userId = UUID.randomUUID()
        val passwordHash = BCrypt.hashpw(request.password, BCrypt.gensalt())

        val existing = jdbi.withHandle<String?, Exception> { handle ->
            handle.createQuery("SELECT id FROM users WHERE username = ?")
                .bind(0, request.username)
                .mapTo(String::class.java)
                .firstOrNull()
        }
        if (existing != null) {
            return Result.failure(IllegalArgumentException("Username already exists"))
        }

        jdbi.useHandle<Exception> { handle ->
            handle.createUpdate(
                """
                INSERT INTO users (id, username, email, password_hash, created_at, updated_at, is_admin)
                VALUES (?, ?, ?, ?, ?, ?, 0)
            """,
            )
                .bind(0, userId.toString())
                .bind(1, request.username)
                .bind(2, request.email)
                .bind(3, passwordHash)
                .bind(4, now.toString())
                .bind(5, now.toString())
                .execute()
        }

        val user =
            User(
                id = userId,
                username = request.username,
                email = request.email,
                passwordHash = passwordHash,
                createdAt = now,
                updatedAt = now,
                isAdmin = false,
            )
        val token = jwtService.generateToken(user)
        val refreshToken = issueRefreshToken(userId)
        logger.info("User registered: ${request.username}")

        return Result.success(
            LoginResponse(
                token = token,
                user = UserDto(userId.toString(), request.username, request.email, now.toString(), false),
                refreshToken = refreshToken,
            ),
        )
    }

    fun login(request: LoginRequest): Result<LoginResponse> {
        val credential = request.username.trim()
        val user =
            jdbi.withHandle<User, Exception> { handle ->
                handle.createQuery("SELECT * FROM users WHERE username = ? OR email = ?")
                    .bind(0, credential)
                    .bind(1, credential)
                    .map { row -> mapUser(row) }
                    .firstOrNull()
            } ?: return Result.failure(IllegalArgumentException("Invalid username or password"))

        if (!BCrypt.checkpw(request.password, user.passwordHash)) {
            return Result.failure(IllegalArgumentException("Invalid username or password"))
        }

        val token = jwtService.generateToken(user)
        val refreshToken = issueRefreshToken(user.id)
        logger.info("User logged in: ${user.username}")

        return Result.success(
            LoginResponse(
                token = token,
                user = UserDto(user.id.toString(), user.username, user.email, user.createdAt.toString(), user.isAdmin),
                refreshToken = refreshToken,
            ),
        )
    }

    fun seedDevUser(): LoginResponse? {
        val devUsername = "dev"
        val devEmail = "dev@booktower.local"
        val devPassword = "dev12345"

        val existing = jdbi.withHandle<String?, Exception> { handle ->
            handle.createQuery("SELECT id FROM users WHERE username = ?")
                .bind(0, devUsername)
                .mapTo(String::class.java)
                .firstOrNull()
        }

        if (existing != null) {
            logger.info("Dev user already exists")
            return login(LoginRequest(devUsername, devPassword)).getOrNull()
        }

        val result = register(CreateUserRequest(devUsername, devEmail, devPassword))
        result.getOrNull()?.user?.id?.let { userId ->
            jdbi.useHandle<Exception> { handle ->
                handle.createUpdate("UPDATE users SET is_admin = true WHERE id = ?")
                    .bind(0, userId)
                    .execute()
            }
        }
        return login(LoginRequest(devUsername, devPassword)).getOrNull()
    }

    fun changePassword(userId: UUID, currentPassword: String, newPassword: String): Result<Unit> {
        val user = getUserById(userId)
            ?: return Result.failure(IllegalArgumentException("User not found"))

        if (!BCrypt.checkpw(currentPassword, user.passwordHash)) {
            return Result.failure(IllegalArgumentException("Current password is incorrect"))
        }

        val newHash = BCrypt.hashpw(newPassword, BCrypt.gensalt())
        jdbi.useHandle<Exception> { handle ->
            handle.createUpdate("UPDATE users SET password_hash = ?, updated_at = ? WHERE id = ?")
                .bind(0, newHash)
                .bind(1, Instant.now().toString())
                .bind(2, userId.toString())
                .execute()
        }

        logger.info("Password changed for user: ${user.username}")
        return Result.success(Unit)
    }

    fun changeEmail(userId: UUID, currentPassword: String, newEmail: String): Result<Unit> {
        val user = getUserById(userId)
            ?: return Result.failure(IllegalArgumentException("User not found"))

        if (!BCrypt.checkpw(currentPassword, user.passwordHash)) {
            return Result.failure(IllegalArgumentException("Current password is incorrect"))
        }

        val trimmedEmail = newEmail.trim().lowercase()
        if (!trimmedEmail.contains("@") || trimmedEmail.length > 100) {
            return Result.failure(IllegalArgumentException("Invalid email address"))
        }

        val existing = jdbi.withHandle<String?, Exception> { handle ->
            handle.createQuery("SELECT id FROM users WHERE email = ? AND id != ?")
                .bind(0, trimmedEmail)
                .bind(1, userId.toString())
                .mapTo(String::class.java)
                .firstOrNull()
        }
        if (existing != null) {
            return Result.failure(IllegalArgumentException("Email already in use"))
        }

        jdbi.useHandle<Exception> { handle ->
            handle.createUpdate("UPDATE users SET email = ?, updated_at = ? WHERE id = ?")
                .bind(0, trimmedEmail)
                .bind(1, Instant.now().toString())
                .bind(2, userId.toString())
                .execute()
        }

        logger.info("Email changed for user: ${user.username}")
        return Result.success(Unit)
    }

    /** Issues a long-lived refresh token, stores it in DB, returns the opaque token string. */
    fun issueRefreshToken(userId: UUID): String {
        val token = UUID.randomUUID().toString()
        val expiresAt = Instant.now().plus(30, java.time.temporal.ChronoUnit.DAYS)
        jdbi.useHandle<Exception> { handle ->
            handle.createUpdate(
                "INSERT INTO refresh_tokens (token, user_id, expires_at, created_at) VALUES (?, ?, ?, ?)"
            )
                .bind(0, token)
                .bind(1, userId.toString())
                .bind(2, expiresAt.toString())
                .bind(3, Instant.now().toString())
                .execute()
        }
        return token
    }

    /** Validates a refresh token, rotates it, and returns a new LoginResponse with fresh access + refresh tokens. */
    fun refreshAccessToken(refreshToken: String): Result<LoginResponse> {
        val row = jdbi.withHandle<Map<String, String>?, Exception> { handle ->
            handle.createQuery("SELECT user_id, expires_at FROM refresh_tokens WHERE token = ?")
                .bind(0, refreshToken)
                .map { r ->
                    mapOf(
                        "userId" to r.getColumn("user_id", String::class.java),
                        "expiresAt" to r.getColumn("expires_at", String::class.java),
                    )
                }
                .firstOrNull()
        } ?: return Result.failure(IllegalArgumentException("Invalid or expired refresh token"))

        val expiresAt = parseTimestamp(row["expiresAt"]!!)
        if (Instant.now().isAfter(expiresAt)) {
            jdbi.useHandle<Exception> { h ->
                h.createUpdate("DELETE FROM refresh_tokens WHERE token = ?").bind(0, refreshToken).execute()
            }
            return Result.failure(IllegalArgumentException("Refresh token expired"))
        }

        val userId = UUID.fromString(row["userId"]!!)
        val user = getUserById(userId)
            ?: return Result.failure(IllegalArgumentException("User not found"))

        // Rotate: delete old token, issue new one
        jdbi.useHandle<Exception> { h ->
            h.createUpdate("DELETE FROM refresh_tokens WHERE token = ?").bind(0, refreshToken).execute()
        }
        val newRefreshToken = issueRefreshToken(userId)
        val accessToken = jwtService.generateToken(user)

        return Result.success(
            LoginResponse(
                token = accessToken,
                user = UserDto(user.id.toString(), user.username, user.email, user.createdAt.toString(), user.isAdmin),
                refreshToken = newRefreshToken,
            )
        )
    }

    /** Revokes a specific refresh token. */
    fun revokeRefreshToken(refreshToken: String) {
        jdbi.useHandle<Exception> { handle ->
            handle.createUpdate("DELETE FROM refresh_tokens WHERE token = ?").bind(0, refreshToken).execute()
        }
    }

    /** Revokes all refresh tokens for a user (e.g., on logout-all). */
    fun revokeAllRefreshTokens(userId: UUID) {
        jdbi.useHandle<Exception> { handle ->
            handle.createUpdate("DELETE FROM refresh_tokens WHERE user_id = ?").bind(0, userId.toString()).execute()
        }
    }

    /**
     * OIDC backchannel logout: finds the user by their OIDC sub claim and revokes all
     * their refresh tokens. Returns the number of tokens revoked, or -1 if user not found.
     */
    fun backchannelLogout(sub: String): Int {
        val user = jdbi.withHandle<User?, Exception> { h ->
            h.createQuery("SELECT * FROM users WHERE oidc_sub = ?")
                .bind(0, sub).map { row -> mapUser(row) }.firstOrNull()
        } ?: return -1
        return jdbi.withHandle<Int, Exception> { h ->
            h.createUpdate("DELETE FROM refresh_tokens WHERE user_id = ?")
                .bind(0, user.id.toString()).execute()
        }
    }

    /** Validates username/email + password without issuing a JWT. Used by OPDS Basic Auth. */
    fun getUserByCredentials(usernameOrEmail: String, password: String): User? {
        val credential = usernameOrEmail.trim()
        val user = jdbi.withHandle<User?, Exception> { handle ->
            handle.createQuery("SELECT * FROM users WHERE username = ? OR email = ?")
                .bind(0, credential)
                .bind(1, credential)
                .map { row -> mapUser(row) }
                .firstOrNull()
        } ?: return null
        return if (BCrypt.checkpw(password, user.passwordHash)) user else null
    }

    fun getUserById(userId: UUID): User? {
        return jdbi.withHandle<User?, Exception> { handle ->
            handle.createQuery("SELECT * FROM users WHERE id = ?")
                .bind(0, userId.toString())
                .map { row -> mapUser(row) }
                .firstOrNull()
        }
    }

    /**
     * Finds an existing user by their OIDC subject identifier, or creates a new account.
     * Returns the user and a fresh JWT token.
     */
    fun findOrCreateOidcUser(sub: String, email: String?, name: String?, preferredUsername: String?, isAdminByGroup: Boolean = false): LoginResponse {
        val now = Instant.now()
        // Look up by oidc_sub if the column exists, else by email
        val existing = jdbi.withHandle<User?, Exception> { h ->
            h.createQuery("SELECT * FROM users WHERE oidc_sub = ?")
                .bind(0, sub)
                .map { row -> mapUser(row) }
                .firstOrNull()
        } ?: email?.let { e ->
            jdbi.withHandle<User?, Exception> { h ->
                h.createQuery("SELECT * FROM users WHERE email = ?")
                    .bind(0, e)
                    .map { row -> mapUser(row) }
                    .firstOrNull()
            }
        }

        val user = if (existing != null) {
            // Update oidc_sub and sync admin status from group mapping
            jdbi.useHandle<Exception> { h ->
                h.createUpdate("UPDATE users SET oidc_sub = ?, is_admin = ?, updated_at = ? WHERE id = ?")
                    .bind(0, sub).bind(1, isAdminByGroup).bind(2, now.toString()).bind(3, existing.id.toString()).execute()
            }
            existing.copy(isAdmin = isAdminByGroup)
        } else {
            // Create new account
            val userId = UUID.randomUUID()
            val username = (preferredUsername ?: name?.replace(" ", "_") ?: email?.substringBefore('@') ?: "oidc_$sub")
                .take(50).replace(Regex("[^a-zA-Z0-9_.-]"), "_")
            val uniqueUsername = ensureUniqueUsername(username)
            jdbi.useHandle<Exception> { h ->
                h.createUpdate(
                    """INSERT INTO users (id, username, email, password_hash, oidc_sub, created_at, updated_at, is_admin)
                       VALUES (?, ?, ?, '', ?, ?, ?, ?)""",
                )
                    .bind(0, userId.toString()).bind(1, uniqueUsername).bind(2, email ?: "")
                    .bind(3, sub).bind(4, now.toString()).bind(5, now.toString())
                    .bind(6, isAdminByGroup).execute()
            }
            User(id = userId, username = uniqueUsername, email = email ?: "", passwordHash = "",
                createdAt = now, updatedAt = now, isAdmin = isAdminByGroup)
        }

        val token = jwtService.generateToken(user)
        val userDto = org.booktower.models.UserDto(
            id = user.id.toString(),
            username = user.username,
            email = user.email,
            createdAt = user.createdAt.toString(),
            isAdmin = user.isAdmin,
        )
        return LoginResponse(token = token, user = userDto)
    }

    private fun ensureUniqueUsername(base: String): String {
        var candidate = base
        var suffix = 1
        while (jdbi.withHandle<Boolean, Exception> { h ->
                h.createQuery("SELECT COUNT(*) FROM users WHERE username = ?")
                    .bind(0, candidate).mapTo(Int::class.java).firstOrNull()!! > 0
            }) {
            candidate = "${base}_$suffix"
            suffix++
        }
        return candidate
    }

    private fun mapUser(row: RowView): User {
        return User(
            id = UUID.fromString(row.getColumn("id", String::class.java)),
            username = row.getColumn("username", String::class.java),
            email = row.getColumn("email", String::class.java),
            passwordHash = row.getColumn("password_hash", String::class.java),
            createdAt = parseTimestamp(row.getColumn("created_at", String::class.java)),
            updatedAt = parseTimestamp(row.getColumn("updated_at", String::class.java)),
            isAdmin = row.getColumn("is_admin", java.lang.Boolean::class.java)?.booleanValue() ?: false,
        )
    }

    private fun parseTimestamp(value: String): Instant {
        return try {
            Instant.parse(value)
        } catch (e: Exception) {
            LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss[.SSSSSS][.SSSSS][.SSSS][.SSS][.SS][.S]"))
                .toInstant(ZoneOffset.UTC)
        }
    }
}
