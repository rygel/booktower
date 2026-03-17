package org.booktower.services

import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private val logger = LoggerFactory.getLogger("booktower.BackgroundTaskService")

enum class TaskStatus { RUNNING, DONE, FAILED, CANCELLED }

data class BackgroundTask(
    val id: String,
    val userId: String,
    val type: String,          // e.g. "library.scan", "metadata.fetch"
    val label: String,         // human-readable description
    val status: TaskStatus,
    val startedAt: String,
    val completedAt: String? = null,
    val detail: String? = null, // e.g. "+12 added, 3 skipped" on completion
)

/**
 * In-memory registry for all background tasks.
 * Keeps up to [maxTasks] entries; oldest completed/failed tasks are evicted first.
 */
class BackgroundTaskService(private val maxTasks: Int = 500) {

    private val tasks = ConcurrentHashMap<String, BackgroundTask>()

    fun start(userId: UUID, type: String, label: String): String {
        val id = UUID.randomUUID().toString()
        val task = BackgroundTask(
            id = id,
            userId = userId.toString(),
            type = type,
            label = label,
            status = TaskStatus.RUNNING,
            startedAt = Instant.now().toString(),
        )
        evictIfNeeded()
        tasks[id] = task
        logger.debug("Task started: $id type=$type label=$label user=$userId")
        return id
    }

    fun complete(taskId: String, detail: String? = null) {
        tasks.computeIfPresent(taskId) { _, t ->
            t.copy(status = TaskStatus.DONE, completedAt = Instant.now().toString(), detail = detail)
        }
    }

    fun fail(taskId: String, detail: String? = null) {
        tasks.computeIfPresent(taskId) { _, t ->
            t.copy(status = TaskStatus.FAILED, completedAt = Instant.now().toString(), detail = detail)
        }
    }

    fun dismiss(taskId: String, userId: UUID): Boolean {
        val task = tasks[taskId] ?: return false
        if (task.userId != userId.toString()) return false
        if (task.status == TaskStatus.RUNNING) return false
        tasks.remove(taskId)
        return true
    }

    fun listForUser(userId: UUID): List<BackgroundTask> =
        tasks.values
            .filter { it.userId == userId.toString() }
            .sortedByDescending { it.startedAt }

    fun listAll(): List<BackgroundTask> =
        tasks.values.sortedByDescending { it.startedAt }

    fun get(taskId: String): BackgroundTask? = tasks[taskId]

    private fun evictIfNeeded() {
        if (tasks.size < maxTasks) return
        // Remove oldest completed/failed first, then oldest running
        val candidates = tasks.values
            .filter { it.status != TaskStatus.RUNNING }
            .sortedBy { it.startedAt }
        val toRemove = candidates.take((tasks.size - maxTasks + 1).coerceAtLeast(1))
        toRemove.forEach { tasks.remove(it.id) }
    }
}
