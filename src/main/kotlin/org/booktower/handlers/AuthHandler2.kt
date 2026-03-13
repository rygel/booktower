package org.booktower.handlers

import org.booktower.services.AuthService
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status

class AuthHandler2(private val authService: AuthService) {
    fun register(req: Request): Response {
        return Response(Status.NOT_IMPLEMENTED).body("Register not implemented")
    }

    fun login(req: Request): Response {
        return Response(Status.NOT_IMPLEMENTED).body("Login not implemented")
    }

    fun logout(req: Request): Response {
        return Response(Status.OK).body("Logout not implemented")
    }
}
