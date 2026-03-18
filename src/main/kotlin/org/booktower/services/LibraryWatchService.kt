package org.booktower.services

import org.jdbi.v3.core.Jdbi
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardWatchEventKinds.ENTRY_CREATE
import java.nio.file.StandardWatchEventKinds.ENTRY_DELETE
import java.nio.file.WatchKey
import java.util.UUID
import java.util.concurrent.Executors

private val logger = LoggerFactory.getLogger("booktower.LibraryWatchService")

private val SCANNABLE_EXTENSIONS =
    setOf("pdf", "epub", "mobi", "azw3", "cbz", "cbr", "fb2", "djvu", "mp3", "m4b", "m4a", "ogg", "flac", "aac")

class LibraryWatchService(
    private val jdbi: Jdbi,
    private val libraryService: LibraryService,
) {
    private val watchService = FileSystems.getDefault().newWatchService()
    private val keyToLibrary = mutableMapOf<WatchKey, LibraryRef>()
    private val executor =
        Executors.newSingleThreadExecutor { r ->
            Thread(r, "library-watcher").also { it.isDaemon = true }
        }

    private data class LibraryRef(
        val userId: UUID,
        val libraryId: UUID,
        val path: String,
    )

    fun start() {
        registerAllLibraries()
        executor.submit(::watchLoop)
        logger.info("Library watcher started (watching ${keyToLibrary.size} libraries)")
        Runtime.getRuntime().addShutdownHook(Thread { stop() })
    }

    fun stop() {
        executor.shutdownNow()
        runCatching { watchService.close() }
        logger.info("Library watcher stopped")
    }

    /** Call after a library is created so its path is watched immediately. */
    fun registerLibrary(
        userId: UUID,
        libraryId: UUID,
        path: String,
    ) {
        val dir = File(path)
        if (!dir.exists() || !dir.isDirectory) return
        val key = Paths.get(path).register(watchService, ENTRY_CREATE, ENTRY_DELETE)
        synchronized(keyToLibrary) { keyToLibrary[key] = LibraryRef(userId, libraryId, path) }
        logger.info("Now watching library path: $path")
    }

    /** Call after a library is deleted to stop watching its path. */
    fun unregisterLibrary(libraryId: UUID) {
        synchronized(keyToLibrary) {
            val entry = keyToLibrary.entries.find { it.value.libraryId == libraryId }
            if (entry != null) {
                entry.key.cancel()
                keyToLibrary.remove(entry.key)
                logger.info("Stopped watching library: $libraryId")
            }
        }
    }

    private fun registerAllLibraries() {
        val libraries =
            jdbi.withHandle<List<Triple<UUID, UUID, String>>, Exception> { handle ->
                handle
                    .createQuery("SELECT user_id, id, path FROM libraries")
                    .map { rs, _ ->
                        Triple(
                            UUID.fromString(rs.getString("user_id")),
                            UUID.fromString(rs.getString("id")),
                            rs.getString("path"),
                        )
                    }.list()
            }
        for ((userId, libraryId, path) in libraries) {
            val dir = File(path)
            if (!dir.exists() || !dir.isDirectory) {
                logger.warn("Skipping watch for missing library path: $path")
                continue
            }
            runCatching {
                val key = Paths.get(path).register(watchService, ENTRY_CREATE, ENTRY_DELETE)
                keyToLibrary[key] = LibraryRef(userId, libraryId, path)
            }.onFailure { e -> logger.warn("Failed to watch $path: ${e.message}") }
        }
    }

    private fun watchLoop() {
        while (!Thread.currentThread().isInterrupted) {
            val key =
                try {
                    watchService.take()
                } catch (_: InterruptedException) {
                    break
                } catch (e: Exception) {
                    logger.warn("WatchService error: ${e.message}")
                    break
                }

            val ref =
                synchronized(keyToLibrary) { keyToLibrary[key] } ?: run {
                    key.reset()
                    continue
                }

            for (event in key.pollEvents()) {
                val kind = event.kind()

                @Suppress("UNCHECKED_CAST")
                val relativePath = event.context() as? Path ?: continue
                val file = File(ref.path, relativePath.toString())
                val ext = file.extension.lowercase()

                when (kind) {
                    ENTRY_CREATE -> {
                        if (ext in SCANNABLE_EXTENSIONS) {
                            logger.info("Watcher detected new file: ${file.absolutePath} — triggering scan")
                            runCatching { libraryService.scanLibrary(ref.userId, ref.libraryId) }
                                .onFailure { e -> logger.warn("Watcher scan failed for ${ref.libraryId}: ${e.message}") }
                        }
                    }
                    ENTRY_DELETE -> {
                        if (ext in SCANNABLE_EXTENSIONS) {
                            logger.info("Watcher detected deleted file: ${file.absolutePath} — marking book missing")
                            markBookMissing(file.absolutePath)
                        }
                    }
                }
            }

            if (!key.reset()) {
                synchronized(keyToLibrary) { keyToLibrary.remove(key) }
                logger.warn("Watch key invalidated for library: ${ref.path}")
            }
        }
    }

    private fun markBookMissing(absolutePath: String) {
        jdbi.useHandle<Exception> { handle ->
            handle
                .createUpdate("UPDATE books SET file_missing = true, updated_at = ? WHERE file_path = ?")
                .bind(
                    0,
                    java.time.Instant
                        .now()
                        .toString(),
                ).bind(1, absolutePath)
                .execute()
        }
    }
}
