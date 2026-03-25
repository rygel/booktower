package org.runary.models

import java.time.Instant
import java.util.UUID

data class User(
    val id: UUID,
    val username: String,
    val email: String,
    val passwordHash: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val isAdmin: Boolean = false,
)

data class UserDto(
    val id: String,
    val username: String,
    val email: String,
    val createdAt: String,
    val isAdmin: Boolean,
)

data class CreateUserRequest(
    val username: String,
    val email: String,
    val password: String,
)

data class LoginRequest(
    val username: String,
    val password: String,
)

data class LoginResponse(
    val token: String,
    val user: UserDto,
    val refreshToken: String? = null,
)

data class ChangePasswordRequest(
    val currentPassword: String,
    val newPassword: String,
)

data class ChangeEmailRequest(
    val currentPassword: String,
    val newEmail: String,
)

data class UserAdminDto(
    val id: String,
    val username: String,
    val email: String,
    val createdAt: String,
    val isAdmin: Boolean,
)

data class SetAdminRequest(
    val isAdmin: Boolean,
)

// ── API Tokens ─────────────────────────────────────────────────────────────────

data class ApiTokenDto(
    val id: String,
    val name: String,
    val createdAt: String,
    val lastUsedAt: String?,
)

data class CreateApiTokenRequest(
    val name: String,
)

data class CreatedApiTokenResponse(
    val id: String,
    val name: String,
    val token: String,
    val createdAt: String,
)
