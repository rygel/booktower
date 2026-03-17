package org.booktower.integration

import org.booktower.config.Json
import org.booktower.services.AuditService
import org.booktower.services.GeoIpService
import org.booktower.services.GeoLocation
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class GeoIpAuditIntegrationTest : IntegrationTestBase() {

    private fun auditService(): AuditService {
        val jdbi = org.booktower.TestFixture.database.getJdbi()
        val geoIp = object : GeoIpService() {
            override fun lookup(ip: String) = GeoLocation("US", "United States", "Test City")
        }
        return AuditService(jdbi, geoIp)
    }

    @Test
    fun `login with X-Forwarded-For records geo fields in audit log`() {
        val username = "geotest_${System.nanoTime()}"
        // Register first
        app(
            Request(Method.POST, "/auth/register")
                .header("Content-Type", "application/json")
                .body("""{"username":"$username","email":"$username@test.com","password":"password123"}"""),
        )

        // Login with spoofed IP
        val resp = app(
            Request(Method.POST, "/auth/login")
                .header("Content-Type", "application/json")
                .header("X-Forwarded-For", "203.0.113.42")
                .body("""{"username":"$username","password":"password123"}"""),
        )
        assertEquals(Status.OK, resp.status)

        // Read audit log directly via AuditService
        val svc = auditService()
        val entries = svc.listRecent(10)
        val loginEntry = entries.find { it.action == "user.login" && it.actorName == username }
        assertNotNull(loginEntry, "Expected a user.login audit entry for $username")
        assertEquals("203.0.113.42", loginEntry!!.ipAddress)
        assertEquals("US", loginEntry.countryCode)
        assertEquals("United States", loginEntry.countryName)
        assertEquals("Test City", loginEntry.city)
    }

    @Test
    fun `register with X-Forwarded-For records geo fields in audit log`() {
        val username = "georeg_${System.nanoTime()}"
        app(
            Request(Method.POST, "/auth/register")
                .header("Content-Type", "application/json")
                .header("X-Forwarded-For", "198.51.100.7")
                .body("""{"username":"$username","email":"$username@test.com","password":"password123"}"""),
        )

        val svc = auditService()
        val entries = svc.listRecent(10)
        val entry = entries.find { it.action == "user.register" && it.actorName == username }
        assertNotNull(entry, "Expected a user.register audit entry for $username")
        assertEquals("198.51.100.7", entry!!.ipAddress)
        assertEquals("US", entry.countryCode)
    }

    @Test
    fun `login without IP header leaves geo fields null`() {
        val username = "nogeo_${System.nanoTime()}"
        app(
            Request(Method.POST, "/auth/register")
                .header("Content-Type", "application/json")
                .body("""{"username":"$username","email":"$username@test.com","password":"password123"}"""),
        )
        app(
            Request(Method.POST, "/auth/login")
                .header("Content-Type", "application/json")
                .body("""{"username":"$username","password":"password123"}"""),
        )

        val svc = auditService()
        val entry = svc.listRecent(20).find { it.action == "user.login" && it.actorName == username }
        assertNotNull(entry)
        assertNull(entry!!.ipAddress)
        assertNull(entry.countryCode)
        assertNull(entry.countryName)
    }

    @Test
    fun `GeoIpService returns private network for loopback address`() {
        val svc = GeoIpService()
        val loc = svc.lookup("127.0.0.1")
        assertNotNull(loc)
        assertEquals("Private Network", loc!!.countryName)
        assertNull(loc.countryCode)
    }

    @Test
    fun `GeoIpService returns private network for site-local address`() {
        val svc = GeoIpService()
        val loc = svc.lookup("192.168.1.100")
        assertNotNull(loc)
        assertEquals("Private Network", loc!!.countryName)
    }

    @Test
    fun `GeoIpService returns null for blank IP`() {
        val svc = GeoIpService()
        assertNull(svc.lookup(""))
    }
}
