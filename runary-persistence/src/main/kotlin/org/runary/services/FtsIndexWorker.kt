package org.runary.services

import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

private val workerLog = LoggerFactory.getLogger("runary.FtsIndexWorker")

class FtsIndexWorker(
    private val ftsService: FtsService,
    private val backgroundTaskService: BackgroundTaskService,
    private val throttleMs: Long = 300,
    private val pollIntervalMs: Long = 10_000,
) {
    private val running = AtomicBoolean(false)
    private val executor =
        Executors.newSingleThreadExecutor { r ->
            Thread(r, "fts-indexer").also {
                it.isDaemon = true
                it.priority = Thread.MIN_PRIORITY
            }
        }

    fun start() {
        if (!ftsService.isActive()) {
            workerLog.debug("FTS not active — index worker not started")
            return
        }
        running.set(true)
        executor.submit(::loop)
        workerLog.info("FTS index worker started (throttle=${throttleMs}ms, poll=${pollIntervalMs}ms, priority=MIN)")
    }

    fun stop() {
        running.set(false)
        executor.shutdown()
        executor.awaitTermination(10, TimeUnit.SECONDS)
        workerLog.info("FTS index worker stopped")
    }

    private fun loop() {
        while (running.get()) {
            try {
                val processed = processBatch()
                if (processed == 0 && running.get()) {
                    Thread.sleep(pollIntervalMs)
                }
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            } catch (e: Exception) {
                workerLog.error("FTS worker error", e)
                if (running.get()) Thread.sleep(pollIntervalMs)
            }
        }
    }

    private fun processBatch(): Int {
        val counts = runCatching { ftsService.countByStatus() }.getOrDefault(emptyMap())
        val pending = counts["pending"] ?: 0L
        if (pending == 0L) return 0

        // System sentinel UUID — FTS tasks are not user-scoped
        val systemId = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val taskId = backgroundTaskService.start(systemId, "fts.index", "FTS indexing: $pending book(s) pending")

        var done = 0
        var failed = 0

        while (running.get()) {
            val batch = runCatching { ftsService.fetchPending(5) }.getOrNull() ?: break
            if (batch.isEmpty()) break
            for (book in batch) {
                if (!running.get()) break
                val ok =
                    runCatching {
                        ftsService.indexBook(book.bookId, book.filePath, book.format, book.language)
                    }.getOrElse { e ->
                        workerLog.warn("FTS indexing failed for ${book.bookId}: ${e.message}")
                        false
                    }
                if (ok) done++ else failed++
                workerLog.debug("FTS indexed ${book.bookId} ok=$ok ($done done, $failed failed)")
                if (throttleMs > 0 && running.get()) Thread.sleep(throttleMs)
            }
        }

        backgroundTaskService.complete(taskId, "$done indexed, $failed failed (of ${done + failed} processed)")
        return done + failed
    }
}
