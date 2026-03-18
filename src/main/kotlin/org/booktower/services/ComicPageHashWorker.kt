package org.booktower.services

import org.slf4j.LoggerFactory
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private val log = LoggerFactory.getLogger("booktower.ComicPageHashWorker")

/**
 * Background worker that processes the comic_hash_queue and computes aHash
 * for every page of each queued comic book.
 *
 * Runs in a single MIN_PRIORITY daemon thread; polls every [pollIntervalMs] ms
 * and sleeps [throttleMs] between books so it doesn't saturate the CPU.
 */
class ComicPageHashWorker(
    private val service: ComicPageHashService,
    private val backgroundTaskService: BackgroundTaskService,
    val throttleMs: Long = 500,
    val pollIntervalMs: Long = 15_000,
) {
    private val executor =
        Executors.newSingleThreadExecutor { r ->
            Thread(r, "comic-hasher").also {
                it.isDaemon = true
                it.priority = Thread.MIN_PRIORITY
            }
        }

    @Volatile private var running = false

    fun start() {
        running = true
        executor.submit { loop() }
        log.info("ComicPageHashWorker started")
    }

    fun stop() {
        running = false
        executor.shutdown()
        executor.awaitTermination(5, TimeUnit.SECONDS)
    }

    private fun loop() {
        while (running) {
            try {
                processBatch()
            } catch (e: Exception) {
                log.warn("Comic page hash batch error: ${e.message}")
            }
            if (running) Thread.sleep(pollIntervalMs)
        }
    }

    private fun processBatch() {
        val pending = service.fetchPending(10)
        if (pending.isEmpty()) return

        val counts = service.countByStatus()
        val systemUser = java.util.UUID.fromString("00000000-0000-0000-0000-000000000000")
        val taskId =
            backgroundTaskService.start(
                systemUser,
                "comic.page.hash",
                "Hashing pages for ${counts["pending"] ?: 0} comic book(s)",
            )

        for (book in pending) {
            if (!running) break
            try {
                service.indexBook(book.bookId, book.filePath)
            } catch (e: Exception) {
                log.warn("Failed to hash pages for book ${book.bookId}: ${e.message}")
                service.markStatus(book.bookId, "failed", e.message?.take(500))
            }
            if (throttleMs > 0 && running) Thread.sleep(throttleMs)
        }
        backgroundTaskService.complete(taskId)
    }
}
