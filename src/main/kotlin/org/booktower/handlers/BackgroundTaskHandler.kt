package org.booktower.handlers

import org.booktower.config.Json
import org.booktower.filters.AuthenticatedUser
import org.booktower.models.ErrorResponse
import org.booktower.services.BackgroundTaskService
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status

class BackgroundTaskHandler(private val taskService: BackgroundTaskService) {

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
        val taskId = req.uri.path.split("/").lastOrNull()
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

    /** GET /api/admin/tasks — returns all tasks across all users (admin only) */
    fun listAll(req: Request): Response {
        val tasks = taskService.listAll()
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(Json.mapper.writeValueAsString(tasks))
    }
}
