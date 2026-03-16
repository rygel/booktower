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

        jdbi.useHandle<Exception> { handle ->
            val existing =
                handle.createQuery("SELECT id FROM users WHERE username = ?")
                    .bind(0, request.username)
                    .mapTo(String::class.java)
                    .firstOrNull()

            require(existing == null) { "Username already exists" }

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
        logger.info("User registered: ${request.username}")

        return Result.success(
            LoginResponse(
                token = token,
                user = UserDto(userId.toString(), request.username, request.email, now.toString(), false),
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
        logger.info("User logged in: ${user.username}")

        return Result.success(
            LoginResponse(
                token = token,
                user = UserDto(user.id.toString(), user.username, user.email, user.createdAt.toString(), user.isAdmin),
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
