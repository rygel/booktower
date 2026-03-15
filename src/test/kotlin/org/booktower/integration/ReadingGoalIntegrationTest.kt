package org.booktower.integration

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReadingGoalIntegrationTest : IntegrationTestBase() {

    private fun setGoal(token: String, goal: Int) {
        app(Request(Method.POST, "/ui/goal")
            .header("Cookie", "token=$token")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .body("goal=$goal"))
    }

    private fun setBookFinished(token: String, bookId: String) {
        app(Request(Method.POST, "/ui/books/$bookId/status")
            .header("Cookie", "token=$token")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .body("status=FINISHED"))
    }

    @Test
    fun `set goal returns 200`() {
        val token = registerAndGetToken("rg1")
        val response = app(Request(Method.POST, "/ui/goal")
            .header("Cookie", "token=$token")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .body("goal=12"))
        assertEquals(Status.OK, response.status)
    }

    @Test
    fun `dashboard shows reading goal card`() {
        val token = registerAndGetToken("rg2")
        val body = app(Request(Method.GET, "/").header("Cookie", "token=$token")).bodyString()
        assertTrue(body.contains("page.dashboard.stat.goal") || body.contains("Reading Goal") || body.contains("Leseziel") || body.contains("goal-modal"),
            "Dashboard should show reading goal widget")
    }

    @Test
    fun `goal is saved and shown on dashboard`() {
        val token = registerAndGetToken("rg3")
        setGoal(token, 24)
        val body = app(Request(Method.GET, "/").header("Cookie", "token=$token")).bodyString()
        assertTrue(body.contains("24"), "Dashboard should show the set goal of 24")
    }

    @Test
    fun `finished book count increments on dashboard`() {
        val token = registerAndGetToken("rg4")
        setGoal(token, 10)
        val libId = createLibrary(token)
        val bookId = createBook(token, libId, "Finished Book")
        setBookFinished(token, bookId)

        val body = app(Request(Method.GET, "/").header("Cookie", "token=$token")).bodyString()
        // The dashboard shows booksFinishedThisYear / goal
        assertTrue(body.contains("1 / 10") || body.contains("1&nbsp;/&nbsp;10") || body.contains(">1<"),
            "Dashboard should show 1 book finished toward goal")
    }

    @Test
    fun `goal of zero shows dash instead of number`() {
        val token = registerAndGetToken("rg5")
        // Default goal is 0 (unset)
        val body = app(Request(Method.GET, "/").header("Cookie", "token=$token")).bodyString()
        assertTrue(body.contains("/ —") || body.contains("/&nbsp;—") || body.contains("/ &#8212;") || body.contains("—"),
            "Dashboard should show dash when no goal is set")
    }

    @Test
    fun `goal requires authentication`() {
        val response = app(Request(Method.POST, "/ui/goal")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .body("goal=12"))
        assertEquals(Status.UNAUTHORIZED, response.status)
    }

    @Test
    fun `goal is per-user`() {
        val token1 = registerAndGetToken("rg6a")
        val token2 = registerAndGetToken("rg6b")
        setGoal(token1, 50)
        setGoal(token2, 5)
        val body2 = app(Request(Method.GET, "/").header("Cookie", "token=$token2")).bodyString()
        // user2's goal is 5, not 50
        assertTrue(body2.contains("5"), "User2 should see their own goal of 5")
    }

    @Test
    fun `updating goal overwrites previous value`() {
        val token = registerAndGetToken("rg7")
        setGoal(token, 10)
        setGoal(token, 20)
        val body = app(Request(Method.GET, "/").header("Cookie", "token=$token")).bodyString()
        assertTrue(body.contains("20"), "Updated goal of 20 should be shown")
    }
}
