package org.booktower.handlers

import org.booktower.config.Json
import org.booktower.filters.AuthenticatedUser
import org.booktower.models.ErrorResponse
import org.booktower.services.BackgroundTaskService
import org.booktower.services.SeedService
import org.booktower.services.TaskStatus
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status

class BackgroundTaskHandler(
    private val taskService: BackgroundTaskService,
    private val seedService: SeedService? = null,
) {
    /** GET /api/tasks — returns the authenticated user's task list */
    fun list(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val tasks = taskService.listForUser(userId)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(Json.mapper.writeValueAsString(tasks))
    }

    /** DELETE /api/tasks/{id} — dismisses a completed or failed task */
    fun dismiss(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val taskId =
            req.uri.path
                .split("/")
                .lastOrNull()
                ?: return Response(Status.BAD_REQUEST)
                    .header("Content-Type", "application/json")
                    .body(Json.mapper.writeValueAsString(ErrorResponse("BAD_REQUEST", "Missing task ID")))
        return if (taskService.dismiss(taskId, userId)) {
            Response(Status.NO_CONTENT)
        } else {
            Response(Status.NOT_FOUND)
                .header("Content-Type", "application/json")
                .body(Json.mapper.writeValueAsString(ErrorResponse("NOT_FOUND", "Task not found or still running")))
        }
    }

    /** POST /api/tasks/{id}/retry — retries a failed download task */
    fun retry(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val taskId =
            req.uri.path
                .split("/")
                .dropLast(1)
                .lastOrNull()
                ?: return Response(Status.BAD_REQUEST)
                    .header("Content-Type", "application/json")
                    .body(Json.mapper.writeValueAsString(ErrorResponse("BAD_REQUEST", "Missing task ID")))
        val task = taskService.get(taskId)
            ?: return Response(Status.NOT_FOUND)
                .header("Content-Type", "application/json")
                .body(Json.mapper.writeValueAsString(ErrorResponse("NOT_FOUND", "Task not found")))
        if (task.userId != userId.toString()) {
            return Response(Status.NOT_FOUND)
                .header("Content-Type", "application/json")
                .body(Json.mapper.writeValueAsString(ErrorResponse("NOT_FOUND", "Task not found")))
        }
        if (task.status != TaskStatus.FAILED) {
            return Response(Status.BAD_REQUEST)
                .header("Content-Type", "application/json")
                .body(Json.mapper.writeValueAsString(ErrorResponse("BAD_REQUEST", "Only failed tasks can be retried")))
        }
        val svc = seedService
            ?: return Response(Status.SERVICE_UNAVAILABLE)
                .header("Content-Type", "application/json")
                .body(Json.mapper.writeValueAsString(ErrorResponse("SERVICE_UNAVAILABLE", "Retry not available")))

        // Dismiss the old failed task
        taskService.dismiss(taskId, userId)

        return if (svc.retryDownload(userId, task)) {
            Response(Status.OK)
                .header("Content-Type", "application/json")
                .header("HX-Trigger", """{"showToast":{"message":"Retrying download...","type":"info"}}""")
                .body("""{"retried":true}""")
        } else {
            Response(Status.BAD_REQUEST)
                .header("Content-Type", "application/json")
                .body(Json.mapper.writeValueAsString(ErrorResponse("BAD_REQUEST", "Cannot retry this task type")))
        }
    }

    /** GET /api/admin/tasks — returns all tasks across all users (admin only) */
    fun listAll(req: Request): Response {
        val tasks = taskService.listAll()
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(Json.mapper.writeValueAsString(tasks))
    }
}
