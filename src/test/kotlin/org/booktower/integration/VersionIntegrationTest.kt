package org.booktower.integration

import org.booktower.config.Json
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class VersionIntegrationTest : IntegrationTestBase() {
    @Test
    fun `GET api version returns 200 with version fields`() {
        val resp = app(Request(Method.GET, "/api/version"))
        assertEquals(Status.OK, resp.status)
        val tree = Json.mapper.readTree(resp.bodyString())
        assert(tree.has("version")) { "missing version field" }
        assert(tree.has("name")) { "missing name field" }
        assert(tree.has("jvm")) { "missing jvm field" }
        assert(tree.has("kotlinVersion")) { "missing kotlinVersion field" }
    }

    @Test
    fun `GET api version is accessible without authentication`() {
        val resp = app(Request(Method.GET, "/api/version"))
        assertEquals(Status.OK, resp.status)
    }

    @Test
    fun `GET api version version field is non-blank`() {
        val resp = app(Request(Method.GET, "/api/version"))
        val tree = Json.mapper.readTree(resp.bodyString())
        assertFalse(tree.get("version").asText().isBlank(), "version should not be blank")
    }

    @Test
    fun `GET api version kotlinVersion matches running kotlin version`() {
        val resp = app(Request(Method.GET, "/api/version"))
        val tree = Json.mapper.readTree(resp.bodyString())
        assertEquals(KotlinVersion.CURRENT.toString(), tree.get("kotlinVersion").asText())
    }

    @Test
    fun `GET api version jvm field contains java version`() {
        val resp = app(Request(Method.GET, "/api/version"))
        val tree = Json.mapper.readTree(resp.bodyString())
        val jvm = tree.get("jvm").asText()
        assertFalse(jvm.isBlank(), "jvm should not be blank")
        assert(jvm.contains(System.getProperty("java.version"))) {
            "jvm field '$jvm' should contain java.version '${System.getProperty("java.version")}'"
        }
    }
}
