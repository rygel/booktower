package org.booktower.handlers

import org.booktower.config.Json
import org.booktower.models.CreateUserRequest
import org.booktower.models.ErrorResponse
import org.booktower.models.LoginRequest
import org.booktower.services.AuthService
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.body.form
import org.http4k.core.cookie.Cookie
import org.http4k.core.cookie.cookie
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("booktower.AuthHandler")

class AuthHandler2(private val authService: AuthService) {
    fun register(req: Request): Response {
        return try {
            val createRequest = parseRegisterRequest(req)
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
        return try {
            val loginRequest = parseLoginRequest(req)
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

    fun logout(req: Request): Response {
        logger.info("User logged out")
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .cookie(createLogoutCookie())
            .body(Json.mapper.writeValueAsString(mapOf("message" to "Logged out successfully")))
    }

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

    private fun createAuthCookie(token: String): Cookie {
        return Cookie(
            name = "token",
            value = token,
            httpOnly = true,
            secure = false,
            path = "/",
            maxAge = 60 * 60 * 24 * 7,
        )
    }

    private fun createLogoutCookie(): Cookie {
        return Cookie(
            name = "token",
            value = "",
            httpOnly = true,
            secure = false,
            path = "/",
            maxAge = 0,
        )
    }
}
