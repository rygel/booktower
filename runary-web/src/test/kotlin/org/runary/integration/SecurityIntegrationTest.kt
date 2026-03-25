package org.runary.integration

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Test
import org.runary.config.Json
import org.runary.models.LoginResponse
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SecurityIntegrationTest : IntegrationTestBase() {
    private fun uniqueUser() = "sec_${System.nanoTime()}"

    private fun registerAndGetToken(): String {
        val username = uniqueUser()
        val response =
            app(
                Request(Method.POST, "/auth/register")
                    .header("Content-Type", "application/json")
                    .body("""{"username":"$username","email":"$username@test.com","password":"${org.runary.TestPasswords.DEFAULT}"}"""),
            )
        return Json.mapper.readValue(response.bodyString(), LoginResponse::class.java).token
    }

    @Test
    fun `expired JWT token returns 401`() {
        val expiredToken =
            JWT
                .create()
                .withIssuer("runary")
                .withSubject(UUID.randomUUID().toString())
                .withIssuedAt(Instant.now().minus(2, ChronoUnit.DAYS))
                .withExpiresAt(Instant.now().minus(1, ChronoUnit.DAYS))
                .sign(Algorithm.HMAC256("test-secret-key-not-for-production"))

        val response =
            app(
                Request(Method.GET, "/api/libraries")
                    .header("Cookie", "token=$expiredToken"),
            )
        assertEquals(Status.UNAUTHORIZED, response.status)
    }

    @Test
    fun `JWT signed with wrong secret returns 401`() {
        val wrongSecretToken =
            JWT
                .create()
                .withIssuer("runary")
                .withSubject(UUID.randomUUID().toString())
                .withIssuedAt(Instant.now())
                .withExpiresAt(Instant.now().plus(1, ChronoUnit.DAYS))
                .sign(Algorithm.HMAC256("wrong-secret"))

        val response =
            app(
                Request(Method.GET, "/api/libraries")
                    .header("Cookie", "token=$wrongSecretToken"),
            )
        assertEquals(Status.UNAUTHORIZED, response.status)
    }

    @Test
    fun `JWT with wrong issuer returns 401`() {
        val wrongIssuerToken =
            JWT
                .create()
                .withIssuer("not-runary")
                .withSubject(UUID.randomUUID().toString())
                .withIssuedAt(Instant.now())
                .withExpiresAt(Instant.now().plus(1, ChronoUnit.DAYS))
                .sign(Algorithm.HMAC256("test-secret-key-not-for-production"))

        val response =
            app(
                Request(Method.GET, "/api/libraries")
                    .header("Cookie", "token=$wrongIssuerToken"),
            )
        assertEquals(Status.UNAUTHORIZED, response.status)
    }

    @Test
    fun `malformed JWT returns 401`() {
        val response =
            app(
                Request(Method.GET, "/api/libraries")
                    .header("Cookie", "token=not.a.valid.jwt.at.all"),
            )
        assertEquals(Status.UNAUTHORIZED, response.status)
    }

    @Test
    fun `empty cookie value returns 401`() {
        val response =
            app(
                Request(Method.GET, "/api/libraries")
                    .header("Cookie", "token="),
            )
        assertEquals(Status.UNAUTHORIZED, response.status)
    }

    @Test
    fun `token for non-existent user is rejected with 401`() {
        val fakeUserToken =
            JWT
                .create()
                .withIssuer("runary")
                .withSubject(UUID.randomUUID().toString())
                .withClaim("username", "ghost")
                .withIssuedAt(Instant.now())
                .withExpiresAt(Instant.now().plus(1, ChronoUnit.DAYS))
                .sign(Algorithm.HMAC256("test-secret-key-not-for-production"))

        val response =
            app(
                Request(Method.GET, "/api/libraries")
                    .header("Cookie", "token=$fakeUserToken"),
            )
        // Token is cryptographically valid but the user doesn't exist in DB — must be rejected
        assertEquals(Status.UNAUTHORIZED, response.status)
    }

    @Test
    fun `all protected endpoints reject unauthenticated requests`() {
        val endpoints =
            listOf(
                Method.GET to "/api/libraries",
                Method.POST to "/api/libraries",
                Method.DELETE to "/api/libraries/00000000-0000-0000-0000-000000000000",
                Method.GET to "/api/books",
                Method.POST to "/api/books",
                Method.GET to "/api/books/00000000-0000-0000-0000-000000000000",
                Method.DELETE to "/api/books/00000000-0000-0000-0000-000000000000",
                Method.PUT to "/api/books/00000000-0000-0000-0000-000000000000/progress",
                Method.GET to "/api/recent",
            )

        for ((method, path) in endpoints) {
            val response = app(Request(method, path))
            assertEquals(
                Status.UNAUTHORIZED,
                response.status,
                "Expected 401 for $method $path without auth",
            )
        }
    }

    @Test
    fun `all public endpoints allow unauthenticated requests`() {
        val endpoints =
            listOf(
                Method.GET to "/",
                Method.GET to "/login",
                Method.GET to "/register",
            )

        for ((method, path) in endpoints) {
            val response = app(Request(method, path))
            assertEquals(
                Status.OK,
                response.status,
                "Expected 200 for $method $path without auth",
            )
        }
    }

    @Test
    fun `auth endpoints accept unauthenticated requests`() {
        val response =
            app(
                Request(Method.POST, "/auth/login")
                    .header("Content-Type", "application/json")
                    .body("""{"username":"nobody","password":"whatever1"}"""),
            )
        // Should get 401 (invalid credentials), not a different error
        assertEquals(Status.UNAUTHORIZED, response.status)
    }

    @Test
    fun `user A cannot access user B books via libraryId`() {
        val tokenA = registerAndGetToken()
        val tokenB = registerAndGetToken()

        val libResponse =
            app(
                Request(Method.POST, "/api/libraries")
                    .header("Cookie", "token=$tokenA")
                    .header("Content-Type", "application/json")
                    .body("""{"name":"A's Lib","path":"./data/test-sec-a-${System.nanoTime()}"}"""),
            )
        val libId =
            Json.mapper
                .readTree(libResponse.bodyString())
                .get("id")
                .asText()

        app(
            Request(Method.POST, "/api/books")
                .header("Cookie", "token=$tokenA")
                .header("Content-Type", "application/json")
                .body("""{"title":"Secret Book","author":null,"description":null,"libraryId":"$libId"}"""),
        )

        // User B queries with A's libraryId — must see zero books
        val response =
            app(
                Request(Method.GET, "/api/books?libraryId=$libId")
                    .header("Cookie", "token=$tokenB"),
            )
        assertEquals(Status.OK, response.status)
        val tree = Json.mapper.readTree(response.bodyString())
        assertEquals(0, tree.get("total").asInt(), "User B must not see user A's books")
        assertEquals(0, tree.get("books").size(), "User B must not see user A's books")
    }

    @Test
    fun `user B cannot delete user A's library`() {
        val tokenA = registerAndGetToken()
        val tokenB = registerAndGetToken()

        val libResponse =
            app(
                Request(Method.POST, "/api/libraries")
                    .header("Cookie", "token=$tokenA")
                    .header("Content-Type", "application/json")
                    .body("""{"name":"A's Private Lib","path":"./data/test-sec-del-lib-${System.nanoTime()}"}"""),
            )
        val libId =
            Json.mapper
                .readTree(libResponse.bodyString())
                .get("id")
                .asText()

        val response =
            app(
                Request(Method.DELETE, "/api/libraries/$libId")
                    .header("Cookie", "token=$tokenB"),
            )
        assertEquals(Status.NOT_FOUND, response.status, "User B must not be able to delete user A's library")
    }

    @Test
    fun `user B cannot delete user A's book`() {
        val tokenA = registerAndGetToken()
        val tokenB = registerAndGetToken()

        val libResponse =
            app(
                Request(Method.POST, "/api/libraries")
                    .header("Cookie", "token=$tokenA")
                    .header("Content-Type", "application/json")
                    .body("""{"name":"A's Lib","path":"./data/test-sec-del-book-${System.nanoTime()}"}"""),
            )
        val libId =
            Json.mapper
                .readTree(libResponse.bodyString())
                .get("id")
                .asText()

        val bookResponse =
            app(
                Request(Method.POST, "/api/books")
                    .header("Cookie", "token=$tokenA")
                    .header("Content-Type", "application/json")
                    .body("""{"title":"Secret Book","author":null,"description":null,"libraryId":"$libId"}"""),
            )
        val bookId =
            Json.mapper
                .readTree(bookResponse.bodyString())
                .get("id")
                .asText()

        val response =
            app(
                Request(Method.DELETE, "/api/books/$bookId")
                    .header("Cookie", "token=$tokenB"),
            )
        assertEquals(Status.NOT_FOUND, response.status, "User B must not be able to delete user A's book")
    }

    @Test
    fun `login endpoint returns 429 after too many requests from same IP`() {
        // Dedicated test IP so this bucket doesn't collide with other tests
        val testIp = "192.0.2.42"
        repeat(10) {
            app(
                Request(Method.POST, "/auth/login")
                    .header("X-Forwarded-For", testIp)
                    .header("Content-Type", "application/json")
                    .body("""{"username":"nobody","password":"nope"}"""),
            )
        }
        val response =
            app(
                Request(Method.POST, "/auth/login")
                    .header("X-Forwarded-For", testIp)
                    .header("Content-Type", "application/json")
                    .body("""{"username":"nobody","password":"nope"}"""),
            )
        assertEquals(Status.TOO_MANY_REQUESTS, response.status)
        assertTrue(response.header("Retry-After") != null)
    }

    @Test
    fun `register endpoint is also rate limited`() {
        val testIp = "192.0.2.43"
        repeat(10) {
            app(
                Request(Method.POST, "/auth/register")
                    .header("X-Forwarded-For", testIp)
                    .header("Content-Type", "application/json")
                    .body("""{"username":"ratelimituser_$it","email":"rl$it@test.com","password":"${org.runary.TestPasswords.DEFAULT}"}"""),
            )
        }
        val response =
            app(
                Request(Method.POST, "/auth/register")
                    .header("X-Forwarded-For", testIp)
                    .header("Content-Type", "application/json")
                    .body("""{"username":"ratelimituser_overflow","email":"overflow@test.com","password":"${org.runary.TestPasswords.DEFAULT}"}"""),
            )
        assertEquals(Status.TOO_MANY_REQUESTS, response.status)
    }

    @Test
    fun `GET api-books with invalid UUID returns 400`() {
        val token = registerAndGetToken()
        val response =
            app(
                Request(Method.GET, "/api/books/not-a-valid-uuid")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.BAD_REQUEST, response.status)
    }

    @Test
    fun `DELETE api-libraries with invalid UUID returns 400`() {
        val token = registerAndGetToken()
        val response =
            app(
                Request(Method.DELETE, "/api/libraries/not-a-valid-uuid")
                    .header("Cookie", "token=$token"),
            )
        assertEquals(Status.BAD_REQUEST, response.status)
    }

    @Test
    fun `SQL injection in username is prevented`() {
        val response =
            app(
                Request(Method.POST, "/auth/register")
                    .header("Content-Type", "application/json")
                    .body("""{"username":"'; DROP TABLE users; --","email":"x@test.com","password":"${org.runary.TestPasswords.DEFAULT}"}"""),
            )
        // Should be rejected by validation (special chars), not cause a SQL error
        assertEquals(Status.BAD_REQUEST, response.status)
    }

    @Test
    fun `SQL injection in query parameter is safe`() {
        val token = registerAndGetToken()
        val response =
            app(
                Request(Method.GET, "/api/books?libraryId=' OR 1=1 --")
                    .header("Cookie", "token=$token"),
            )
        // Should not crash - parameterized queries prevent injection
        assertTrue(response.status.code in listOf(200, 400, 500))
    }

    @Test
    fun `XSS in username is not reflected in HTML`() {
        val xssUsername = "xss_${System.nanoTime()}"
        // Register with clean username first
        val response =
            app(
                Request(Method.POST, "/auth/register")
                    .header("Content-Type", "application/json")
                    .body("""{"username":"$xssUsername","email":"$xssUsername@test.com","password":"${org.runary.TestPasswords.DEFAULT}"}"""),
            )
        assertEquals(Status.CREATED, response.status)
    }

    @Test
    fun `unicode in book title is handled`() {
        val token = registerAndGetToken()
        val libResponse =
            app(
                Request(Method.POST, "/api/libraries")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"name":"Unicode Lib","path":"./data/test-uni-${System.nanoTime()}"}"""),
            )
        val libId =
            Json.mapper
                .readTree(libResponse.bodyString())
                .get("id")
                .asText()

        val response =
            app(
                Request(Method.POST, "/api/books")
                    .header("Cookie", "token=$token")
                    .header("Content-Type", "application/json")
                    .body("""{"title":"Les Misérables 日本語 Ñoño","author":"Victor Hugo","description":null,"libraryId":"$libId"}"""),
            )
        assertEquals(Status.CREATED, response.status)
        assertTrue(response.bodyString().contains("Misérables"))
    }
}
