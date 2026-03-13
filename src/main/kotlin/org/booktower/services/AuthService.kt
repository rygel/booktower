package org.booktower.services

import org.booktower.models.*
import org.jdbi.v3.core.Jdbi
import org.jdbi.v3.core.result.RowView
import org.mindrot.jbcrypt.BCrypt
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

private val logger = LoggerFactory.getLogger("booktower.AuthService")

class AuthService(
    private val jdbi: Jdbi,
    private val jwtService: JwtService,
) {
    fun register(request: CreateUserRequest): Result<LoginResponse> {
        val now = Instant.now()
        val userId = UUID.randomUUID()

        jdbi.useHandle<Exception> { handle ->
            val existing =
                handle.createQuery("SELECT id FROM users WHERE username = ?")
                    .bind(0, request.username)
                    .mapTo(String::class.java)
                    .first()

            if (existing != null) {
                throw IllegalArgumentException("Username already exists")
            }

            val passwordHash = BCrypt.hashpw(request.password, BCrypt.gensalt())

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

        val token = jwtService.generateToken(User(userId, request.username, request.email, "", now, now, false))
        logger.info("User registered: ${request.username}")

        return Result.success(
            LoginResponse(
                token = token,
                user = UserDto(userId.toString(), request.username, request.email, now.toString(), false),
            ),
        )
    }

    fun login(request: LoginRequest): Result<LoginResponse> {
        val user =
            jdbi.withHandle<User?, Exception> { handle ->
                handle.createQuery("SELECT * FROM users WHERE username = ?")
                    .bind(0, request.username)
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
            createdAt = Instant.parse(row.getColumn("created_at", String::class.java)),
            updatedAt = Instant.parse(row.getColumn("updated_at", String::class.java)),
            isAdmin = row.getColumn("is_admin", Boolean::class.java),
        )
    }
}
