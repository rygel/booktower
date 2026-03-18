package org.booktower.handlers

import org.booktower.config.Json
import org.booktower.filters.AuthenticatedUser
import org.booktower.model.ThemeCatalog
import org.booktower.models.ChangeEmailRequest
import org.booktower.models.ChangePasswordRequest
import org.booktower.models.CreateUserRequest
import org.booktower.models.ErrorResponse
import org.booktower.models.LoginRequest
import org.booktower.models.SuccessResponse
import org.booktower.services.AuditService
import org.booktower.services.AuthService
import org.booktower.services.EmailService
import org.booktower.services.PasswordResetService
import org.booktower.services.UserSettingsService
import org.booktower.web.WebContext
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.body.form
import org.http4k.core.cookie.Cookie
import org.http4k.core.cookie.cookie
import org.slf4j.LoggerFactory
import java.util.UUID

private val logger = LoggerFactory.getLogger("booktower.AuthHandler")

// Secure cookies in production; derived from the same env var used by SecurityConfig
private val secureCookies = System.getenv("BOOKTOWER_ENV")?.lowercase() == "production"

class AuthHandler2(
    private val authService: AuthService,
    private val userSettingsService: UserSettingsService,
    private val passwordResetService: PasswordResetService,
    private val emailService: EmailService,
    private val appBaseUrl: String,
    private val registrationOpen: Boolean = true,
    private val auditService: AuditService? = null,
    private val oidcForceOnly: Boolean = false,
) {
    fun register(req: Request): Response {
        if (!registrationOpen) {
            return Response(Status.FORBIDDEN)
                .header("Content-Type", "application/json")
                .body(Json.mapper.writeValueAsString(ErrorResponse("REGISTRATION_CLOSED", "Registration is closed on this server")))
        }
        return try {
            val createRequest =
                parseRegisterRequest(req)
                    ?: return Response(Status.BAD_REQUEST)
                        .header("Content-Type", "application/json")
                        .body(Json.mapper.writeValueAsString(ErrorResponse("VALIDATION_ERROR", "Request body is required")))

            val validationError = validateCreateUserRequest(createRequest)
            if (validationError != null) {
                return Response(Status.BAD_REQUEST)
                    .header("Content-Type", "application/json")
                    .body(Json.mapper.writeValueAsString(ErrorResponse("VALIDATION_ERROR", validationError)))
            }

            val result = authService.register(createRequest)

            result.fold(
                onSuccess = { loginResponse ->
                    logger.info("User registered successfully: ${loginResponse.user.username}")
                    auditService?.record(
                        UUID.fromString(loginResponse.user.id),
                        loginResponse.user.username,
                        "user.register",
                        ipAddress = clientIp(req),
                    )
                    val base =
                        if (isFormRequest(req)) {
                            Response(Status.SEE_OTHER)
                                .header("Location", "/")
                                .cookie(createAuthCookie(loginResponse.token))
                        } else {
                            Response(Status.CREATED)
                                .header("Content-Type", "application/json")
                                .cookie(createAuthCookie(loginResponse.token))
                                .body(Json.mapper.writeValueAsString(loginResponse))
                        }
                    applyPreferenceCookies(base, loginResponse.user.id)
                },
                onFailure = { error ->
                    logger.warn("Registration failed: ${error.message}")
                    when (error) {
                        is IllegalArgumentException ->
                            Response(Status.CONFLICT)
                                .header("Content-Type", "application/json")
                                .body(
                                    Json.mapper.writeValueAsString(
                                        ErrorResponse("USER_EXISTS", error.message ?: "Username already exists"),
                                    ),
                                )

                        else ->
                            Response(Status.INTERNAL_SERVER_ERROR)
                                .header("Content-Type", "application/json")
                                .body(Json.mapper.writeValueAsString(ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred")))
                    }
                },
            )
        } catch (e: Exception) {
            logger.error("Error during registration", e)
            Response(Status.INTERNAL_SERVER_ERROR)
                .header("Content-Type", "application/json")
                .body(Json.mapper.writeValueAsString(ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred")))
        }
    }

    fun login(req: Request): Response {
        if (oidcForceOnly) {
            return Response(Status.FORBIDDEN)
                .header("Content-Type", "application/json")
                .body(
                    Json.mapper.writeValueAsString(ErrorResponse("OIDC_FORCE_ONLY", "Local login is disabled. Please use SSO to sign in.")),
                )
        }
        return try {
            val loginRequest =
                parseLoginRequest(req)
                    ?: return Response(Status.BAD_REQUEST)
                        .header("Content-Type", "application/json")
                        .body(Json.mapper.writeValueAsString(ErrorResponse("VALIDATION_ERROR", "Request body is required")))

            val validationError = validateLoginRequest(loginRequest)
            if (validationError != null) {
                return Response(Status.BAD_REQUEST)
                    .header("Content-Type", "application/json")
                    .body(Json.mapper.writeValueAsString(ErrorResponse("VALIDATION_ERROR", validationError)))
            }

            val result = authService.login(loginRequest)

            result.fold(
                onSuccess = { loginResponse ->
                    logger.info("User logged in successfully: ${loginResponse.user.username}")
                    auditService?.record(
                        UUID.fromString(loginResponse.user.id),
                        loginResponse.user.username,
                        "user.login",
                        ipAddress = clientIp(req),
                    )
                    val base =
                        if (isFormRequest(req)) {
                            Response(Status.SEE_OTHER)
                                .header("Location", "/")
                                .cookie(createAuthCookie(loginResponse.token))
                        } else {
                            Response(Status.OK)
                                .header("Content-Type", "application/json")
                                .cookie(createAuthCookie(loginResponse.token))
                                .body(Json.mapper.writeValueAsString(loginResponse))
                        }
                    applyPreferenceCookies(base, loginResponse.user.id)
                },
                onFailure = { error ->
                    logger.warn("Login failed: ${error.message}")
                    when (error) {
                        is IllegalArgumentException ->
                            Response(Status.UNAUTHORIZED)
                                .header("Content-Type", "application/json")
                                .header("WWW-Authenticate", "Bearer")
                                .body(Json.mapper.writeValueAsString(ErrorResponse("INVALID_CREDENTIALS", "Invalid username or password")))

                        else ->
                            Response(Status.INTERNAL_SERVER_ERROR)
                                .header("Content-Type", "application/json")
                                .body(Json.mapper.writeValueAsString(ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred")))
                    }
                },
            )
        } catch (e: Exception) {
            logger.error("Error during login", e)
            Response(Status.INTERNAL_SERVER_ERROR)
                .header("Content-Type", "application/json")
                .body(Json.mapper.writeValueAsString(ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred")))
        }
    }

    /**
     * POST /auth/refresh
     * Body (JSON): {"refreshToken": "..."}
     * Returns new access token + rotated refresh token.
     */
    fun refresh(req: Request): Response {
        val body =
            try {
                Json.mapper.readTree(req.bodyString())
            } catch (_: Exception) {
                null
            }
        val token = body?.get("refreshToken")?.asText()
        if (token.isNullOrBlank()) {
            return Response(Status.BAD_REQUEST)
                .header("Content-Type", "application/json")
                .body(Json.mapper.writeValueAsString(ErrorResponse("VALIDATION_ERROR", "refreshToken is required")))
        }
        return authService.refreshAccessToken(token).fold(
            onSuccess = { loginResponse ->
                Response(Status.OK)
                    .header("Content-Type", "application/json")
                    .cookie(createAuthCookie(loginResponse.token))
                    .body(Json.mapper.writeValueAsString(loginResponse))
            },
            onFailure = {
                Response(Status.UNAUTHORIZED)
                    .header("Content-Type", "application/json")
                    .body(Json.mapper.writeValueAsString(ErrorResponse("INVALID_TOKEN", it.message ?: "Invalid refresh token")))
            },
        )
    }

    /**
     * POST /auth/revoke
     * Body (JSON): {"refreshToken": "..."}
     * Revokes the given refresh token.
     */
    fun revokeToken(req: Request): Response {
        val body =
            try {
                Json.mapper.readTree(req.bodyString())
            } catch (_: Exception) {
                null
            }
        val token = body?.get("refreshToken")?.asText()
        if (!token.isNullOrBlank()) {
            authService.revokeRefreshToken(token)
        }
        return Response(Status.NO_CONTENT)
    }

    fun logout(req: Request): Response {
        logger.info("User logged out")
        val base = Response(Status.OK).cookie(createLogoutCookie())
        return if (req.header("HX-Request") != null) {
            base.header("HX-Redirect", "/login")
        } else {
            Response(Status.SEE_OTHER).cookie(createLogoutCookie()).header("Location", "/login")
        }
    }

    fun changePassword(req: Request): Response {
        val userId: UUID = AuthenticatedUser.from(req)
        return try {
            val body = req.bodyString()
            if (body.isBlank()) {
                return Response(Status.BAD_REQUEST)
                    .header("Content-Type", "application/json")
                    .body(Json.mapper.writeValueAsString(ErrorResponse("VALIDATION_ERROR", "Request body is required")))
            }
            val changeRequest = Json.mapper.readValue(body, ChangePasswordRequest::class.java)

            if (changeRequest.currentPassword.isBlank()) {
                return Response(Status.BAD_REQUEST)
                    .header("Content-Type", "application/json")
                    .body(Json.mapper.writeValueAsString(ErrorResponse("VALIDATION_ERROR", "Current password is required")))
            }
            if (changeRequest.newPassword.isBlank()) {
                return Response(Status.BAD_REQUEST)
                    .header("Content-Type", "application/json")
                    .body(Json.mapper.writeValueAsString(ErrorResponse("VALIDATION_ERROR", "New password is required")))
            }
            if (changeRequest.newPassword.length < 8) {
                return Response(Status.BAD_REQUEST)
                    .header("Content-Type", "application/json")
                    .body(Json.mapper.writeValueAsString(ErrorResponse("VALIDATION_ERROR", "New password must be at least 8 characters")))
            }

            val result =
                authService.changePassword(
                    userId,
                    changeRequest.currentPassword,
                    changeRequest.newPassword,
                )

            result.fold(
                onSuccess = {
                    logger.info("Password changed for user $userId")
                    Response(Status.OK)
                        .header("Content-Type", "application/json")
                        .body(Json.mapper.writeValueAsString(SuccessResponse("Password changed successfully")))
                },
                onFailure = { error ->
                    logger.warn("Password change failed for user $userId: ${error.message}")
                    when (error) {
                        is IllegalArgumentException ->
                            Response(Status.BAD_REQUEST)
                                .header("Content-Type", "application/json")
                                .body(Json.mapper.writeValueAsString(ErrorResponse("VALIDATION_ERROR", error.message ?: "Invalid request")))
                        else ->
                            Response(Status.INTERNAL_SERVER_ERROR)
                                .header("Content-Type", "application/json")
                                .body(Json.mapper.writeValueAsString(ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred")))
                    }
                },
            )
        } catch (e: Exception) {
            logger.error("Error during password change", e)
            Response(Status.INTERNAL_SERVER_ERROR)
                .header("Content-Type", "application/json")
                .body(Json.mapper.writeValueAsString(ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred")))
        }
    }

    fun changeEmail(req: Request): Response {
        val userId: UUID = AuthenticatedUser.from(req)
        return try {
            val body = req.bodyString()
            if (body.isBlank()) {
                return Response(Status.BAD_REQUEST)
                    .header("Content-Type", "application/json")
                    .body(Json.mapper.writeValueAsString(ErrorResponse("VALIDATION_ERROR", "Request body is required")))
            }
            val changeRequest = Json.mapper.readValue(body, ChangeEmailRequest::class.java)

            if (changeRequest.currentPassword.isBlank()) {
                return Response(Status.BAD_REQUEST)
                    .header("Content-Type", "application/json")
                    .body(Json.mapper.writeValueAsString(ErrorResponse("VALIDATION_ERROR", "Current password is required")))
            }
            if (changeRequest.newEmail.isBlank()) {
                return Response(Status.BAD_REQUEST)
                    .header("Content-Type", "application/json")
                    .body(Json.mapper.writeValueAsString(ErrorResponse("VALIDATION_ERROR", "New email is required")))
            }

            val result =
                authService.changeEmail(
                    userId,
                    changeRequest.currentPassword,
                    changeRequest.newEmail,
                )

            result.fold(
                onSuccess = {
                    logger.info("Email changed for user $userId")
                    Response(Status.OK)
                        .header("Content-Type", "application/json")
                        .body(Json.mapper.writeValueAsString(SuccessResponse("Email updated successfully")))
                },
                onFailure = { error ->
                    logger.warn("Email change failed for user $userId: ${error.message}")
                    when (error) {
                        is IllegalArgumentException ->
                            Response(Status.BAD_REQUEST)
                                .header("Content-Type", "application/json")
                                .body(Json.mapper.writeValueAsString(ErrorResponse("VALIDATION_ERROR", error.message ?: "Invalid request")))
                        else ->
                            Response(Status.INTERNAL_SERVER_ERROR)
                                .header("Content-Type", "application/json")
                                .body(Json.mapper.writeValueAsString(ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred")))
                    }
                },
            )
        } catch (e: Exception) {
            logger.error("Error during email change", e)
            Response(Status.INTERNAL_SERVER_ERROR)
                .header("Content-Type", "application/json")
                .body(Json.mapper.writeValueAsString(ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred")))
        }
    }

    /**
     * POST /auth/forgot-password
     * Body (form or JSON): email=...
     * Always returns 200 (to not reveal whether the email exists).
     * The raw token is logged at INFO level for the self-hosted admin.
     */
    fun forgotPassword(req: Request): Response {
        val email =
            if (isFormRequest(req)) {
                req.form("email")
            } else {
                try {
                    Json.mapper
                        .readTree(req.bodyString())
                        .get("email")
                        ?.asText()
                } catch (_: Exception) {
                    null
                }
            }
        if (!email.isNullOrBlank()) {
            val token = passwordResetService.createToken(email)
            if (token != null) {
                val resetLink = "$appBaseUrl/reset-password?token=$token"
                emailService.sendPasswordReset(email, resetLink)
                if (!emailService.config.enabled) {
                    // Fallback for self-hosted instances without SMTP
                    logger.info("Password reset link (SMTP not configured — share with user): $resetLink")
                }
            }
        }
        // Always 200 — do not reveal whether email exists
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(Json.mapper.writeValueAsString(SuccessResponse("If that email is registered, a password reset link has been sent.")))
    }

    /**
     * POST /auth/reset-password
     * Body (form or JSON): token=..., newPassword=...
     */
    fun resetPassword(req: Request): Response {
        val (token, newPassword) =
            if (isFormRequest(req)) {
                Pair(req.form("token"), req.form("newPassword"))
            } else {
                val tree =
                    try {
                        Json.mapper.readTree(req.bodyString())
                    } catch (_: Exception) {
                        null
                    }
                Pair(tree?.get("token")?.asText(), tree?.get("newPassword")?.asText())
            }

        if (token.isNullOrBlank()) {
            return Response(Status.BAD_REQUEST)
                .header("Content-Type", "application/json")
                .body(Json.mapper.writeValueAsString(ErrorResponse("VALIDATION_ERROR", "token is required")))
        }
        if (newPassword.isNullOrBlank() || newPassword.length < 8) {
            return Response(Status.BAD_REQUEST)
                .header("Content-Type", "application/json")
                .body(Json.mapper.writeValueAsString(ErrorResponse("VALIDATION_ERROR", "newPassword must be at least 8 characters")))
        }

        val ok = passwordResetService.resetPassword(token, newPassword)
        return if (ok) {
            Response(Status.OK)
                .header("Content-Type", "application/json")
                .body(Json.mapper.writeValueAsString(SuccessResponse("Password reset successfully")))
        } else {
            Response(Status.BAD_REQUEST)
                .header("Content-Type", "application/json")
                .body(Json.mapper.writeValueAsString(ErrorResponse("INVALID_TOKEN", "Token is invalid, expired, or already used")))
        }
    }

    private fun clientIp(req: Request): String? =
        req
            .header("X-Forwarded-For")
            ?.split(",")
            ?.firstOrNull()
            ?.trim()
            ?: req.header("X-Real-IP")

    private fun isFormRequest(req: Request): Boolean {
        val contentType = req.header("Content-Type") ?: ""
        return contentType.contains("application/x-www-form-urlencoded")
    }

    private fun parseLoginRequest(req: Request): LoginRequest? {
        if (isFormRequest(req)) {
            val username = req.form("username") ?: return null
            val password = req.form("password") ?: return null
            return LoginRequest(username, password)
        }
        val body = req.bodyString()
        if (body.isBlank()) return null
        return Json.mapper.readValue(body, LoginRequest::class.java)
    }

    private fun parseRegisterRequest(req: Request): CreateUserRequest? {
        if (isFormRequest(req)) {
            val username = req.form("username") ?: return null
            val email = req.form("email") ?: return null
            val password = req.form("password") ?: return null
            return CreateUserRequest(username, email, password)
        }
        val body = req.bodyString()
        if (body.isBlank()) return null
        return Json.mapper.readValue(body, CreateUserRequest::class.java)
    }

    private fun validateCreateUserRequest(request: CreateUserRequest): String? {
        if (request.username.isBlank()) {
            return "Username is required"
        }
        if (request.username.length < 3 || request.username.length > 50) {
            return "Username must be between 3 and 50 characters"
        }
        if (!request.username.matches(Regex("^[a-zA-Z0-9_]+$"))) {
            return "Username can only contain letters, numbers, and underscores"
        }
        if (request.email.isBlank()) {
            return "Email is required"
        }
        if (!request.email.matches(Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"))) {
            return "Invalid email format"
        }
        if (request.password.isBlank()) {
            return "Password is required"
        }
        if (request.password.length < 8) {
            return "Password must be at least 8 characters"
        }
        return null
    }

    private fun validateLoginRequest(request: LoginRequest): String? {
        if (request.username.isBlank()) {
            return "Username is required"
        }
        if (request.password.isBlank()) {
            return "Password is required"
        }
        return null
    }

    private fun applyPreferenceCookies(
        response: Response,
        userId: String,
    ): Response {
        val settings =
            try {
                userSettingsService.getAll(UUID.fromString(userId))
            } catch (e: Exception) {
                return response
            }
        var r = response
        val theme = settings["theme"]
        if (theme != null && ThemeCatalog.isValid(theme)) {
            r = r.cookie(Cookie(name = "app_theme", value = theme, path = "/", maxAge = 365L * 24 * 3600))
        }
        val lang = settings["language"]
        if (lang != null && lang in WebContext.SUPPORTED_LANGS) {
            r = r.cookie(Cookie(name = "app_lang", value = lang, path = "/", maxAge = 365L * 24 * 3600))
        }
        return r
    }

    private fun createAuthCookie(token: String): Cookie =
        Cookie(
            name = "token",
            value = token,
            httpOnly = true,
            secure = secureCookies,
            path = "/",
            maxAge = 60 * 60 * 24 * 7,
        )

    private fun createLogoutCookie(): Cookie =
        Cookie(
            name = "token",
            value = "",
            httpOnly = true,
            secure = secureCookies,
            path = "/",
            maxAge = 0,
        )
}
