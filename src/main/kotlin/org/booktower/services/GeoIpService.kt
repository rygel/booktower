package org.booktower.services

import org.slf4j.LoggerFactory
import java.net.InetAddress

private val logger = LoggerFactory.getLogger("booktower.GeoIpService")

data class GeoLocation(
    val countryCode: String?,
    val countryName: String?,
    val city: String?,
)

/**
 * Resolves an IP address to a geographic location.
 *
 * Private / loopback / link-local addresses are classified immediately without
 * any network call.  For public IPs, the real implementation calls ip-api.com
 * (no key required, 45 req/min free tier); tests may override [lookup] to
 * return a fixed value without touching the network.
 */
open class GeoIpService {
    /** Override in tests to inject a fixed location without network calls. */
    open fun lookup(ip: String): GeoLocation? {
        if (ip.isBlank()) return null
        return try {
            val addr = InetAddress.getByName(ip)
            when {
                addr.isLoopbackAddress || addr.isLinkLocalAddress || addr.isSiteLocalAddress ->
                    GeoLocation(countryCode = null, countryName = "Private Network", city = null)
                else -> fetchFromApi(ip)
            }
        } catch (e: Exception) {
            logger.debug("GeoIP lookup failed for ip=$ip: ${e.message}")
            null
        }
    }

    private fun fetchFromApi(ip: String): GeoLocation? {
        return try {
            val url = java.net.URL("http://ip-api.com/json/$ip?fields=status,countryCode,country,city")
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 2000
            conn.readTimeout = 2000
            conn.requestMethod = "GET"
            if (conn.responseCode != 200) return null
            val body = conn.inputStream.bufferedReader().readText()
            val tree =
                com.fasterxml.jackson.databind
                    .ObjectMapper()
                    .readTree(body)
            if (tree.get("status")?.asText() != "success") return null
            GeoLocation(
                countryCode = tree.get("countryCode")?.asText()?.takeIf { it.isNotBlank() },
                countryName = tree.get("country")?.asText()?.takeIf { it.isNotBlank() },
                city = tree.get("city")?.asText()?.takeIf { it.isNotBlank() },
            )
        } catch (e: Exception) {
            logger.debug("GeoIP API call failed for ip=$ip: ${e.message}")
            null
        }
    }
}
