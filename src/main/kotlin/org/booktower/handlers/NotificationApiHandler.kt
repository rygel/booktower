package org.booktower.handlers

import org.booktower.config.Json
import org.booktower.filters.AuthenticatedUser
import org.booktower.services.NotificationService
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status

class NotificationApiHandler(
    private val notificationService: NotificationService,
) {
    companion object {
        private const val SSE_HEARTBEAT_SECONDS = 15L
    }

    fun listNotifications(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val unreadOnly = req.uri.query.contains("unread=true")
        val items = notificationService.list(userId, unreadOnly)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                Json.mapper
                    .writeValueAsString(items),
            )
    }

    fun getNotificationCount(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val count = notificationService.unreadCount(userId)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body("""{"count":$count}""")
    }

    fun streamNotifications(req: Request): Response {
        val userId = AuthenticatedUser.from(req)

        // Build an InputStream that streams SSE events. The initial batch is written
        // synchronously so it's immediately available; a daemon thread then appends
        // heartbeats and new-notification checks every SSE_HEARTBEAT_SECONDS.
        val pipeIn = java.io.PipedInputStream(4096)
        val pipeOut = java.io.PipedOutputStream(pipeIn)

        val thread =
            Thread {
                try {
                    pipeOut.use { out ->
                        fun write(s: String) = out.write(s.toByteArray()).also { out.flush() }

                        // Send initial unread notifications
                        val items = notificationService.list(userId, unreadOnly = true)
                        for (item in items) {
                            write("event: notification\ndata: ${Json.mapper.writeValueAsString(item)}\n\n")
                        }
                        write("event: heartbeat\ndata: {}\n\n")

                        // Keep alive: heartbeat + check for new notifications
                        var lastCount = items.size
                        while (!Thread.currentThread().isInterrupted) {
                            Thread.sleep(SSE_HEARTBEAT_SECONDS * 1000)
                            val current = notificationService.list(userId, unreadOnly = true)
                            if (current.size > lastCount) {
                                current.take(current.size - lastCount).forEach { item ->
                                    write("event: notification\ndata: ${Json.mapper.writeValueAsString(item)}\n\n")
                                }
                            }
                            lastCount = current.size
                            write("event: heartbeat\ndata: {}\n\n")
                        }
                    }
                } catch (_: java.io.IOException) {
                    // Client disconnected — expected
                } catch (_: InterruptedException) {
                    // Shutdown — expected
                }
            }
        thread.isDaemon = true
        thread.name = "sse-notif-$userId"
        thread.start()

        return Response(Status.OK)
            .header("Content-Type", "text/event-stream")
            .header("Cache-Control", "no-cache")
            .header("Connection", "keep-alive")
            .body(pipeIn)
    }

    fun markNotificationRead(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val notificationId =
            req.uri.path
                .split("/")
                .dropLast(1)
                .lastOrNull()
                ?: return Response(Status.BAD_REQUEST)
        return if (notificationService.markRead(userId, notificationId)) {
            Response(Status.OK).header("Content-Type", "application/json").body("{}")
        } else {
            Response(Status.NOT_FOUND)
        }
    }

    fun markAllNotificationsRead(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val count = notificationService.markAllRead(userId)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                Json.mapper
                    .writeValueAsString(mapOf("marked" to count)),
            )
    }

    fun deleteNotification(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val notificationId =
            req.uri.path
                .split("/")
                .lastOrNull()
                ?: return Response(Status.BAD_REQUEST)
        return if (notificationService.delete(userId, notificationId)) Response(Status.NO_CONTENT) else Response(Status.NOT_FOUND)
    }
}
