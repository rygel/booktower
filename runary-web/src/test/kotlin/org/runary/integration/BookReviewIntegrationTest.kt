package org.runary.integration

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.runary.config.Json

class BookReviewIntegrationTest : IntegrationTestBase() {
    @Test
    fun `GET reviews returns empty list for new book`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val resp = app(Request(Method.GET, "/api/books/$bookId/reviews").header("Cookie", "token=$token"))
        assertEquals(Status.OK, resp.status)
        assertTrue(Json.mapper.readTree(resp.bodyString()).isArray)
        assertEquals(0, Json.mapper.readTree(resp.bodyString()).size())
    }

    @Test
    fun `POST creates a review`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val resp =
            app(
                Request(Method.POST, "/api/books/$bookId/reviews")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"rating":4,"title":"Great book","body":"I really enjoyed this one.","spoiler":false}"""),
            )
        assertEquals(Status.CREATED, resp.status)
        val tree = Json.mapper.readTree(resp.bodyString())
        assertEquals(4, tree.get("rating").asInt())
        assertEquals("Great book", tree.get("title").asText())
        assertEquals("I really enjoyed this one.", tree.get("body").asText())
        assertFalse(tree.get("spoiler").asBoolean())
    }

    @Test
    fun `GET reviews shows created review`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        app(
            Request(Method.POST, "/api/books/$bookId/reviews")
                .header("Cookie", "token=$token")
                .header("Content-Type", "application/json")
                .body("""{"body":"A thoughtful review.","rating":3}"""),
        )

        val resp = app(Request(Method.GET, "/api/books/$bookId/reviews").header("Cookie", "token=$token"))
        val reviews = Json.mapper.readTree(resp.bodyString())
        assertEquals(1, reviews.size())
        assertEquals("A thoughtful review.", reviews[0].get("body").asText())
    }

    @Test
    fun `PUT updates a review`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val createResp =
            app(
                Request(Method.POST, "/api/books/$bookId/reviews")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"body":"Initial review.","rating":3}"""),
            )
        val reviewId =
            Json.mapper
                .readTree(createResp.bodyString())
                .get("id")
                .asText()

        val putResp =
            app(
                Request(Method.PUT, "/api/books/$bookId/reviews/$reviewId")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"body":"Updated review.","rating":5}"""),
            )
        assertEquals(Status.OK, putResp.status)
        val tree = Json.mapper.readTree(putResp.bodyString())
        assertEquals("Updated review.", tree.get("body").asText())
        assertEquals(5, tree.get("rating").asInt())
    }

    @Test
    fun `DELETE removes a review`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val createResp =
            app(
                Request(Method.POST, "/api/books/$bookId/reviews")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"body":"To be deleted.","rating":2}"""),
            )
        val reviewId =
            Json.mapper
                .readTree(createResp.bodyString())
                .get("id")
                .asText()

        val delResp = app(Request(Method.DELETE, "/api/books/$bookId/reviews/$reviewId").header("Cookie", "token=$token"))
        assertEquals(Status.NO_CONTENT, delResp.status)

        val listResp = app(Request(Method.GET, "/api/books/$bookId/reviews").header("Cookie", "token=$token"))
        assertEquals(0, Json.mapper.readTree(listResp.bodyString()).size())
    }

    @Test
    fun `POST with invalid rating returns 400`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val resp =
            app(
                Request(Method.POST, "/api/books/$bookId/reviews")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"body":"Bad rating.","rating":10}"""),
            )
        assertEquals(Status.BAD_REQUEST, resp.status)
    }

    @Test
    fun `POST with blank body returns 400`() {
        val token = registerAndGetToken()
        val libId = createLibrary(token)
        val bookId = createBook(token, libId)

        val resp =
            app(
                Request(Method.POST, "/api/books/$bookId/reviews")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"body":"","rating":3}"""),
            )
        assertEquals(Status.BAD_REQUEST, resp.status)
    }

    @Test
    fun `reviews endpoints require authentication`() {
        val resp = app(Request(Method.GET, "/api/books/00000000-0000-0000-0000-000000000000/reviews"))
        assertEquals(Status.UNAUTHORIZED, resp.status)
    }
}
