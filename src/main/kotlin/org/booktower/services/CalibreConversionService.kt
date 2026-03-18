package org.booktower.services

import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.TimeUnit

private val logger = LoggerFactory.getLogger("booktower.CalibreConversionService")

/**
 * Converts MOBI / AZW3 files to HTML using Calibre's `ebook-convert` CLI.
 *
 * - Gracefully unavailable when Calibre is not installed (`isAvailable == false`).
 * - Results are cached by (canonical path + last-modified) so repeated reads are cheap.
 * - Conversion timeout is 120 seconds — large books can take tens of seconds.
 */
class CalibreConversionService(
    private val cacheDir: File,
) {
    val isAvailable: Boolean

    init {
        if (!cacheDir.mkdirs() && !cacheDir.exists()) logger.warn("Could not create directory: ${cacheDir.absolutePath}")
        isAvailable = checkCalibre()
        if (isAvailable) {
            logger.info("Calibre ebook-convert found — MOBI/AZW3 in-browser reading enabled")
        } else {
            logger.info("Calibre ebook-convert not found — MOBI/AZW3 will offer download only")
        }
    }

    private fun checkCalibre(): Boolean =
        try {
            val proc =
                ProcessBuilder("ebook-convert", "--version")
                    .redirectErrorStream(true)
                    .start()
            proc.waitFor(10, TimeUnit.SECONDS) && proc.exitValue() == 0
        } catch (_: Exception) {
            false
        }

    /**
     * Converts [file] (mobi or azw3) to a self-contained HTML string.
     * Throws [RuntimeException] if conversion fails.
     */
    fun toHtml(file: File): String {
        val cacheKey = "${file.canonicalPath}|${file.lastModified()}"
        val hash = Integer.toHexString(cacheKey.hashCode())
        val cacheFile = File(cacheDir, "$hash.html")

        if (cacheFile.exists() && cacheFile.length() > 0) {
            logger.debug("Cache hit for ${file.name} → $hash.html")
            return cacheFile.readText()
        }

        logger.info("Converting ${file.name} via Calibre → $hash.html")
        val proc =
            ProcessBuilder(
                "ebook-convert",
                file.canonicalPath,
                cacheFile.canonicalPath,
            ).redirectErrorStream(true).start()

        val output = proc.inputStream.bufferedReader().readText()

        if (!proc.waitFor(120, TimeUnit.SECONDS)) {
            proc.destroyForcibly()
            if (!cacheFile.delete()) logger.warn("Could not delete file: ${cacheFile.absolutePath}")
            throw RuntimeException("Calibre conversion timed out after 120 s for ${file.name}")
        }
        if (proc.exitValue() != 0) {
            if (!cacheFile.delete()) logger.warn("Could not delete file: ${cacheFile.absolutePath}")
            throw RuntimeException("Calibre exited ${proc.exitValue()} for ${file.name}: $output")
        }
        if (!cacheFile.exists() || cacheFile.length() == 0L) {
            throw RuntimeException("Calibre produced no output for ${file.name}")
        }

        return cacheFile.readText()
    }
}
