package org.booktower.services

import org.jdbi.v3.core.Jdbi
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

private val logger = LoggerFactory.getLogger("booktower.ScheduledTaskService")

/** Valid task type slugs that can be scheduled. */
val VALID_TASK_TYPES = setOf("library.scan.all", "covers.regenerate", "metadata.refresh", "export.cleanup")

data class ScheduledTaskDto(
    val id: String,
    val name: String,
    val taskType: String,
    val cronExpression: String,
    val enabled: Boolean,
    val lastRunAt: String?,
    val nextRunAt: String?,
    val createdAt: String,
    val updatedAt: String,
)

data class TaskHistoryDto(
    val id: String,
    val scheduledTaskId: String,
    val startedAt: String,
    val finishedAt: String?,
    val status: String,
    val message: String?,
)

data class CreateScheduledTaskRequest(
    val name: String,
    val taskType: String,
    val cronExpression: String,
    val enabled: Boolean = true,
)

data class UpdateScheduledTaskRequest(
    val name: String? = null,
    val taskType: String? = null,
    val cronExpression: String? = null,
    val enabled: Boolean? = null,
)

class ScheduledTaskService(private val jdbi: Jdbi) {

    fun list(): List<ScheduledTaskDto> =
        jdbi.withHandle<List<ScheduledTaskDto>, Exception> { h ->
            h.createQuery(
                "SELECT id, name, task_type, cron_expression, enabled, last_run_at, next_run_at, created_at, updated_at FROM scheduled_tasks ORDER BY name",
            ).map { row -> mapTask(row) }.list()
        }

    fun get(id: String): ScheduledTaskDto? =
        jdbi.withHandle<ScheduledTaskDto?, Exception> { h ->
            h.createQuery(
                "SELECT id, name, task_type, cron_expression, enabled, last_run_at, next_run_at, created_at, updated_at FROM scheduled_tasks WHERE id = ?",
            ).bind(0, id).map { row -> mapTask(row) }.firstOrNull()
        }

    fun create(request: CreateScheduledTaskRequest): ScheduledTaskDto {
        require(request.name.isNotBlank()) { "Name must not be blank" }
        require(request.taskType in VALID_TASK_TYPES) { "Unknown task type: ${request.taskType}" }
        require(isValidCron(request.cronExpression)) { "Invalid cron expression: ${request.cronExpression}" }

        val id = UUID.randomUUID().toString()
        val now = Instant.now().toString()
        jdbi.useHandle<Exception> { h ->
            h.createUpdate(
                "INSERT INTO scheduled_tasks (id, name, task_type, cron_expression, enabled, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
            ).bind(0, id).bind(1, request.name).bind(2, request.taskType)
                .bind(3, request.cronExpression).bind(4, request.enabled).bind(5, now).bind(6, now).execute()
        }
        return ScheduledTaskDto(
            id = id, name = request.name, taskType = request.taskType,
            cronExpression = request.cronExpression, enabled = request.enabled,
            lastRunAt = null, nextRunAt = null, createdAt = now, updatedAt = now,
        )
    }

    fun update(id: String, request: UpdateScheduledTaskRequest): ScheduledTaskDto? {
        get(id) ?: return null
        request.taskType?.let { require(it in VALID_TASK_TYPES) { "Unknown task type: $it" } }
        request.cronExpression?.let { require(isValidCron(it)) { "Invalid cron expression: $it" } }

        val now = Instant.now().toString()
        val sets = mutableListOf<String>()
        val bindings = mutableListOf<Any?>()
        request.name?.let { sets += "name = ?"; bindings += it }
        request.taskType?.let { sets += "task_type = ?"; bindings += it }
        request.cronExpression?.let { sets += "cron_expression = ?"; bindings += it }
        request.enabled?.let { sets += "enabled = ?"; bindings += it }
        sets += "updated_at = ?"; bindings += now
        bindings += id

        jdbi.useHandle<Exception> { h ->
            val stmt = h.createUpdate("UPDATE scheduled_tasks SET ${sets.joinToString(", ")} WHERE id = ?")
            bindings.forEachIndexed { idx, v -> stmt.bind(idx, v) }
            stmt.execute()
        }
        return get(id)
    }

    fun delete(id: String): Boolean {
        val rows = jdbi.withHandle<Int, Exception> { h ->
            h.createUpdate("DELETE FROM scheduled_tasks WHERE id = ?").bind(0, id).execute()
        }
        return rows > 0
    }

    fun getHistory(scheduledTaskId: String, limit: Int = 50): List<TaskHistoryDto> =
        jdbi.withHandle<List<TaskHistoryDto>, Exception> { h ->
            h.createQuery(
                "SELECT id, scheduled_task_id, started_at, finished_at, status, message FROM task_history WHERE scheduled_task_id = ? ORDER BY started_at DESC LIMIT ?",
            ).bind(0, scheduledTaskId).bind(1, limit).map { row ->
                TaskHistoryDto(
                    id = row.getColumn("id", String::class.java),
                    scheduledTaskId = row.getColumn("scheduled_task_id", String::class.java),
                    startedAt = row.getColumn("started_at", String::class.java),
                    finishedAt = row.getColumn("finished_at", String::class.java),
                    status = row.getColumn("status", String::class.java),
                    message = row.getColumn("message", String::class.java),
                )
            }.list()
        }

    /** Record a task run in the history. Returns the history entry id. */
    fun recordRun(scheduledTaskId: String, status: String, message: String?, startedAt: String, finishedAt: String?): String {
        val id = UUID.randomUUID().toString()
        jdbi.useHandle<Exception> { h ->
            h.createUpdate(
                "INSERT INTO task_history (id, scheduled_task_id, started_at, finished_at, status, message) VALUES (?, ?, ?, ?, ?, ?)",
            ).bind(0, id).bind(1, scheduledTaskId).bind(2, startedAt).bind(3, finishedAt)
                .bind(4, status).bind(5, message).execute()

            h.createUpdate(
                "UPDATE scheduled_tasks SET last_run_at = ?, updated_at = ? WHERE id = ?",
            ).bind(0, finishedAt ?: startedAt).bind(1, Instant.now().toString()).bind(2, scheduledTaskId).execute()
        }
        return id
    }

    /** Trigger a named task immediately and record in history. Returns history id. */
    fun triggerNow(id: String): String? {
        val task = get(id) ?: return null
        val startedAt = Instant.now().toString()
        // We don't have the actual task runner wired here — just record the attempt
        logger.info("Manually triggered task '${task.name}' (${task.taskType})")
        val finishedAt = Instant.now().toString()
        return recordRun(id, "TRIGGERED", "Manually triggered", startedAt, finishedAt)
    }

    private fun isValidCron(expr: String): Boolean {
        // Simple structural validation: must have 5 or 6 space-separated fields
        val parts = expr.trim().split(Regex("\\s+"))
        return parts.size in 5..6
    }

    private fun mapTask(row: org.jdbi.v3.core.result.RowView) = ScheduledTaskDto(
        id = row.getColumn("id", String::class.java),
        name = row.getColumn("name", String::class.java),
        taskType = row.getColumn("task_type", String::class.java),
        cronExpression = row.getColumn("cron_expression", String::class.java),
        enabled = row.getColumn("enabled", java.lang.Boolean::class.java) == true,
        lastRunAt = row.getColumn("last_run_at", String::class.java),
        nextRunAt = row.getColumn("next_run_at", String::class.java),
        createdAt = row.getColumn("created_at", String::class.java),
        updatedAt = row.getColumn("updated_at", String::class.java),
    )
}
