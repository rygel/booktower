package org.booktower.handlers

import org.booktower.services.JwtService
import org.booktower.services.LibraryService
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status

class LibraryHandler2(private val libraryService: LibraryService, private val jwtService: JwtService) {
    fun list(req: Request): Response {
        return Response(Status.NOT_IMPLEMENTED).body("List libraries not implemented")
    }

    fun create(req: Request): Response {
        return Response(Status.NOT_IMPLEMENTED).body("Create library not implemented")
    }
}
