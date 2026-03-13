package org.booktower.handlers

import org.booktower.services.BookService
import org.booktower.services.JwtService
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status

class BookHandler2(private val bookService: BookService, private val jwtService: JwtService) {
    fun list(req: Request): Response {
        return Response(Status.NOT_IMPLEMENTED).body("List books not implemented")
    }

    fun create(req: Request): Response {
        return Response(Status.NOT_IMPLEMENTED).body("Create book not implemented")
    }

    fun recent(req: Request): Response {
        return Response(Status.NOT_IMPLEMENTED).body("Recent books not implemented")
    }
}
