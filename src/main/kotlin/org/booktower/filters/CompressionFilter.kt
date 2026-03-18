package org.booktower.filters

import org.http4k.core.Filter
import org.http4k.core.Response
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

private val COMPRESSIBLE_TYPES = setOf(
    "text/html",
    "text/css",
    "text/javascript",
    "application/javascript",
    "application/json",
    "application/manifest+json",
    "image/svg+xml",
    "text/xml",
    "application/xml",
    "text/plain",
)

private const val MIN_COMPRESS_BYTES = 256

/**
 * Gzip-compresses response bodies for compressible content types when the client
 * sends Accept-Encoding: gzip. Skips binary content (images, audio, PDFs) and
 * responses smaller than 256 bytes.
 */
fun compressionFilter(): Filter = Filter { next ->
    { req ->
        val acceptsGzip = req.header("Accept-Encoding")?.contains("gzip") == true
        val resp = next(req)

        if (!acceptsGzip) {
            resp
        } else {
            val contentType = resp.header("Content-Type") ?: ""
            val baseType = contentType.substringBefore(';').trim().lowercase()
            val isCompressible = COMPRESSIBLE_TYPES.any { baseType == it }

            if (!isCompressible) {
                resp
            } else {
                val body = resp.body.stream.readBytes()
                if (body.size < MIN_COMPRESS_BYTES) {
                    Response(resp.status)
                        .headers(resp.headers)
                        .body(body.inputStream())
                } else {
                    val compressed = ByteArrayOutputStream(body.size / 2).use { baos ->
                        GZIPOutputStream(baos).use { it.write(body) }
                        baos.toByteArray()
                    }
                    Response(resp.status)
                        .headers(resp.headers)
                        .header("Content-Encoding", "gzip")
                        .header("Vary", "Accept-Encoding")
                        .removeHeader("Content-Length")
                        .body(compressed.inputStream())
                }
            }
        }
    }
}
