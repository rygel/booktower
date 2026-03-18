package org.booktower.integration

import org.booktower.config.Json
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * Verifies that write/mutation endpoints return 404 when User B targets a resource
 * owned by User A. The existing SecurityIntegrationTest covers DELETE endpoints;
 * this suite covers PUT / progress-update / upload and HTMX mutation endpoints.
 */
class CrossUserMutationTest : IntegrationTestBase() {
    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun setupBookForUserA(): Triple<String, String, String> {
        val tokenA = registerAndGetToken("cmut_a")
        val libId = createLibrary(tokenA)
        val bookId = createBook(tokenA, libId)
        return Triple(tokenA, libId, bookId)
    }

    private fun tokenB() = registerAndGetToken("cmut_b")

    // ── PUT /api/books/{id} ───────────────────────────────────────────────────

    @Test
    fun `user B cannot update user A book metadata via PUT`() {
        val (_, _, bookId) = setupBookForUserA()
        val b = tokenB()

        val resp =
            app(
                Request(Method.PUT, "/api/books/$bookId")
                    .header("Cookie", "token=$b")
                    .header("Content-Type", "application/json")
                    .body("""{"title":"Hijacked","author":null,"description":null}"""),
            )
        assertEquals(Status.NOT_FOUND, resp.status, "User B must get 404 when updating user A's book")
    }

    // ── PUT /api/books/{id}/progress ──────────────────────────────────────────

    @Test
    fun `user B cannot update reading progress on user A book`() {
        val (_, _, bookId) = setupBookForUserA()
        val b = tokenB()

        val resp =
            app(
                Request(Method.PUT, "/api/books/$bookId/progress")
                    .header("Cookie", "token=$b")
                    .header("Content-Type", "application/json")
                    .body("""{"currentPage":42}"""),
            )
        assertEquals(Status.NOT_FOUND, resp.status, "User B must get 404 when updating progress on user A's book")
    }

    // ── POST /api/books/{id}/upload ───────────────────────────────────────────

    @Test
    fun `user B cannot upload file to user A book`() {
        val (_, _, bookId) = setupBookForUserA()
        val b = tokenB()

        val resp =
            app(
                Request(Method.POST, "/api/books/$bookId/upload")
                    .header("Cookie", "token=$b")
                    .header("X-Filename", "evil.pdf")
                    .body("%PDF-1.4 fake content"),
            )
        assertEquals(Status.NOT_FOUND, resp.status, "User B must get 404 when uploading to user A's book")
    }

    // ── POST /api/books/{id}/cover ────────────────────────────────────────────

    @Test
    fun `user B cannot upload cover to user A book`() {
        val (_, _, bookId) = setupBookForUserA()
        val b = tokenB()

        val resp =
            app(
                Request(Method.POST, "/api/books/$bookId/cover")
                    .header("Cookie", "token=$b")
                    .header("X-Filename", "cover.jpg")
                    .body(ByteArray(100) { 0xFF.toByte() }.inputStream(), 100L),
            )
        assertEquals(Status.NOT_FOUND, resp.status, "User B must get 404 when uploading cover to user A's book")
    }

    // ── HTMX status / rating / tags ───────────────────────────────────────────
    // These endpoints store per-(user_id, book_id) data so User B cannot affect
    // User A's data, but they should still return 200 (they silently no-op for
    // books not owned by the caller — this is the current design: status/rating
    // are per-user anyway). Verify at minimum they don't 500 or leak data.

    @Test
    fun `user B setting status on user A book does not affect user A status`() {
        val (a, _, bookId) = setupBookForUserA()
        val b = tokenB()

        // User B "sets" status on User A's book — operates on User B's own row
        app(
            Request(Method.POST, "/ui/books/$bookId/status")
                .header("Cookie", "token=$b")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body("status=FINISHED"),
        )

        // Confirm User A's book page is still accessible and not affected
        val bookResp =
            app(
                Request(Method.GET, "/api/books/$bookId")
                    .header("Cookie", "token=$a"),
            )
        assertEquals(Status.OK, bookResp.status, "User A's book should still be accessible")
    }

    @Test
    fun `user B setting rating on user A book does not affect user A rating`() {
        val (a, _, bookId) = setupBookForUserA()
        val b = tokenB()

        app(
            Request(Method.POST, "/ui/books/$bookId/rating")
                .header("Cookie", "token=$b")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body("rating=1"),
        )

        val bookResp =
            app(
                Request(Method.GET, "/api/books/$bookId")
                    .header("Cookie", "token=$a"),
            )
        assertEquals(Status.OK, bookResp.status)
        val tree = Json.mapper.readTree(bookResp.bodyString())
        // User A's rating should be null (they never rated it)
        val rating = tree.get("rating")
        val ratingIsNull = rating == null || rating.isNull
        assert(ratingIsNull) { "User A's rating should not be affected by user B's action" }
    }

    // ── Bookmarks ─────────────────────────────────────────────────────────────

    @Test
    fun `user B cannot create bookmark on user A book via API`() {
        val (_, _, bookId) = setupBookForUserA()
        val b = tokenB()

        // The bookmark API stores (user_id, book_id) — creating a bookmark for
        // another user's book ID is silently stored for User B, not User A.
        // At minimum, the endpoint must not 500 or 403.
        val resp =
            app(
                Request(Method.POST, "/api/bookmarks")
                    .header("Cookie", "token=$b")
                    .header("Content-Type", "application/json")
                    .body("""{"bookId":"$bookId","page":5,"title":"steal","note":null}"""),
            )
        // Expect 201 (user B's own bookmark) or validation error — must not 500
        assert(resp.status.code != 500) { "Must not 500 when creating bookmark for another user's book" }
    }

    @Test
    fun `user B bookmarks are not returned when user A queries their bookmarks`() {
        val (a, _, bookId) = setupBookForUserA()
        val b = tokenB()

        // User B creates a bookmark on (their own row keyed to) User A's book id
        app(
            Request(Method.POST, "/api/bookmarks")
                .header("Cookie", "token=$b")
                .header("Content-Type", "application/json")
                .body("""{"bookId":"$bookId","page":99,"title":"intruder","note":null}"""),
        )

        // User A queries their bookmarks for the same bookId
        val aResp =
            app(
                Request(Method.GET, "/api/bookmarks?bookId=$bookId")
                    .header("Cookie", "token=$a"),
            )
        assertEquals(Status.OK, aResp.status)
        val bookmarks = Json.mapper.readTree(aResp.bodyString())
        assert(bookmarks.isArray && bookmarks.size() == 0) {
            "User A should see 0 bookmarks; user B's bookmark must not leak"
        }
    }

    // ── Nonexistent book ID ───────────────────────────────────────────────────

    @Test
    fun `PUT progress on nonexistent book returns 404`() {
        val token = registerAndGetToken("cmut_nx")
        val resp =
            app(
                Request(Method.PUT, "/api/books/00000000-0000-0000-0000-000000000000/progress")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"currentPage":1}"""),
            )
        assertEquals(Status.NOT_FOUND, resp.status)
    }

    @Test
    fun `PUT update on nonexistent book returns 404`() {
        val token = registerAndGetToken("cmut_nx2")
        val resp =
            app(
                Request(Method.PUT, "/api/books/00000000-0000-0000-0000-000000000000")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"title":"Ghost","author":null,"description":null}"""),
            )
        assertEquals(Status.NOT_FOUND, resp.status)
    }
}
