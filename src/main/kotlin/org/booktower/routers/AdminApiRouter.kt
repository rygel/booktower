package org.booktower.routers

import org.booktower.filters.AuthenticatedUser
import org.booktower.handlers.AdminHandler
import org.booktower.handlers.BackgroundTaskHandler
import org.booktower.services.BulkCoverService
import org.booktower.services.BulkMetadataRefreshService
import org.booktower.services.CreateEmailProviderRequest
import org.booktower.services.CreateScheduledTaskRequest
import org.booktower.services.EmailProviderService
import org.booktower.services.ScheduledTaskService
import org.booktower.services.TelemetryService
import org.booktower.services.UpdateEmailProviderRequest
import org.booktower.services.UpdateScheduledTaskRequest
import org.booktower.weblate.WeblateHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.then
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind

class AdminApiRouter(
    private val filters: FilterSet,
    private val adminHandler: AdminHandler,
    private val backgroundTaskHandler: BackgroundTaskHandler?,
    private val weblateHandler: WeblateHandler,
    private val emailProviderService: EmailProviderService?,
    private val scheduledTaskService: ScheduledTaskService?,
    private val bulkCoverService: BulkCoverService?,
    private val telemetryService: TelemetryService?,
    private val bulkMetadataRefreshService: BulkMetadataRefreshService?,
    private val backupService: org.booktower.services.BackupService?,
    private val batchImportService: org.booktower.services.BatchImportService?,
) {
    @Suppress("LongMethod")
    fun routes(): List<RoutingHttpHandler> =
        listOf(
            "/admin/seed" bind Method.POST to filters.admin.then(adminHandler::seed),
            "/admin/seed/full-demo" bind Method.POST to filters.admin.then(adminHandler::seedFullDemo),
            "/admin/seed/files" bind Method.POST to filters.admin.then(adminHandler::seedFiles),
            "/admin/seed/librivox" bind Method.POST to filters.admin.then(adminHandler::seedLibrivox),
            "/admin/seed/comics" bind Method.POST to filters.admin.then(adminHandler::seedComics),
            "/admin/reset-database" bind Method.POST to filters.admin.then(adminHandler::resetDatabase),
            "/api/admin/password-reset-tokens" bind Method.GET to filters.admin.then(adminHandler::listResetTokens),
            "/api/admin/users" bind Method.GET to filters.admin.then(adminHandler::listUsers),
            "/api/admin/users/{userId}/promote" bind Method.POST to filters.admin.then(adminHandler::promote),
            "/api/admin/users/{userId}/demote" bind Method.POST to filters.admin.then(adminHandler::demote),
            "/api/admin/users/{userId}/reset-password" bind Method.POST to filters.admin.then(adminHandler::generateResetLink),
            "/api/admin/users/{userId}/permissions" bind Method.GET to filters.admin.then(adminHandler::getPermissions),
            "/api/admin/users/{userId}/permissions" bind Method.PUT to filters.admin.then(adminHandler::setPermissions),
            "/api/admin/users/{userId}/library-access" bind Method.GET to filters.admin.then(adminHandler::getLibraryAccess),
            "/api/admin/users/{userId}/library-access" bind Method.PUT to filters.admin.then(adminHandler::setLibraryRestricted),
            "/api/admin/users/{userId}/library-access" bind Method.POST to filters.admin.then(adminHandler::grantLibraryAccess),
            "/api/admin/users/{userId}/library-access/{libraryId}" bind Method.DELETE to
                filters.admin.then(adminHandler::revokeLibraryAccess),
            "/api/admin/users/{userId}" bind Method.DELETE to filters.admin.then(adminHandler::deleteUser),
            "/api/admin/email-providers" bind Method.GET to filters.admin.then(::listEmailProviders),
            "/api/admin/email-providers" bind Method.POST to filters.admin.then(::createEmailProvider),
            "/api/admin/email-providers/{id}" bind Method.PUT to filters.admin.then(::updateEmailProvider),
            "/api/admin/email-providers/{id}" bind Method.DELETE to filters.admin.then(::deleteEmailProvider),
            "/api/admin/email-providers/{id}/set-default" bind Method.POST to filters.admin.then(::setDefaultEmailProvider),
            "/api/admin/scheduled-tasks" bind Method.GET to filters.admin.then(::listScheduledTasks),
            "/api/admin/scheduled-tasks" bind Method.POST to filters.admin.then(::createScheduledTask),
            "/api/admin/scheduled-tasks/{id}" bind Method.PUT to filters.admin.then(::updateScheduledTask),
            "/api/admin/scheduled-tasks/{id}" bind Method.DELETE to filters.admin.then(::deleteScheduledTask),
            "/api/admin/scheduled-tasks/{id}/trigger" bind Method.POST to filters.admin.then(::triggerScheduledTask),
            "/api/admin/scheduled-tasks/{id}/history" bind Method.GET to filters.admin.then(::scheduledTaskHistory),
            "/api/covers/regenerate" bind Method.POST to filters.auth.then(::bulkRegenerateCovers),
            "/api/admin/duplicates" bind Method.GET to filters.admin.then(adminHandler::findDuplicates),
            "/api/admin/duplicates/merge" bind Method.POST to filters.admin.then(adminHandler::mergeDuplicates),
            "/api/admin/metadata/refresh" bind Method.POST to filters.admin.then(::bulkMetadataRefresh),
            "/api/admin/comic-page-duplicates" bind Method.GET to filters.admin.then(adminHandler::findComicPageDuplicates),
            "/api/admin/audit" bind Method.GET to filters.admin.then(adminHandler::listAuditLog),
            "/api/admin/tasks" bind Method.GET to
                filters.admin.then(optionalHandler(backgroundTaskHandler?.let { it::listAll })),
            "/api/admin/telemetry/stats" bind Method.GET to filters.admin.then(::telemetryStats),
            // Database backup/restore
            "/api/admin/backup" bind Method.GET to filters.admin.then(::exportBackup),
            "/api/admin/backup" bind Method.POST to filters.admin.then(::importBackup),
            // Batch import from directory
            "/api/admin/import/directory" bind Method.POST to filters.admin.then(::batchImportDirectory),
            // Weblate translation sync (admin only)
            "/api/weblate/pull" bind Method.POST to filters.admin.then(weblateHandler::pull),
            "/api/weblate/push" bind Method.POST to filters.admin.then(weblateHandler::push),
            "/api/weblate/status" bind Method.GET to filters.admin.then(weblateHandler::status),
        )

    // ─── Email providers ─────────────────────────────────────────────────────

    private fun listEmailProviders(req: Request): Response {
        val svc = emailProviderService ?: return Response(Status.SERVICE_UNAVAILABLE)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                org.booktower.config.Json.mapper
                    .writeValueAsString(svc.list()),
            )
    }

    private fun createEmailProvider(req: Request): Response {
        val svc = emailProviderService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val request =
            runCatching {
                org.booktower.config.Json.mapper
                    .readValue(req.bodyString(), CreateEmailProviderRequest::class.java)
            }.getOrNull() ?: return Response(Status.BAD_REQUEST)
                .body("""{"error":"Invalid request body"}""")
        return runCatching { svc.create(request) }
            .fold(
                onSuccess = { dto ->
                    Response(Status.CREATED)
                        .header("Content-Type", "application/json")
                        .body(
                            org.booktower.config.Json.mapper
                                .writeValueAsString(dto),
                        )
                },
                onFailure = { e ->
                    Response(Status.BAD_REQUEST)
                        .header("Content-Type", "application/json")
                        .body("""{"error":"${e.message}"}""")
                },
            )
    }

    private fun updateEmailProvider(req: Request): Response {
        val svc = emailProviderService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val id =
            req.uri.path
                .split("/")
                .lastOrNull() ?: return Response(Status.BAD_REQUEST)
        val request =
            runCatching {
                org.booktower.config.Json.mapper
                    .readValue(req.bodyString(), UpdateEmailProviderRequest::class.java)
            }.getOrNull() ?: return Response(Status.BAD_REQUEST).body("""{"error":"Invalid request body"}""")
        val updated = svc.update(id, request) ?: return Response(Status.NOT_FOUND)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                org.booktower.config.Json.mapper
                    .writeValueAsString(updated),
            )
    }

    private fun deleteEmailProvider(req: Request): Response {
        val svc = emailProviderService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val id =
            req.uri.path
                .split("/")
                .lastOrNull() ?: return Response(Status.BAD_REQUEST)
        return if (svc.delete(id)) Response(Status.NO_CONTENT) else Response(Status.NOT_FOUND)
    }

    private fun setDefaultEmailProvider(req: Request): Response {
        val svc = emailProviderService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val parts = req.uri.path.split("/")
        val id = parts.dropLast(1).lastOrNull() ?: return Response(Status.BAD_REQUEST)
        return if (svc.setDefault(id)) Response(Status.NO_CONTENT) else Response(Status.NOT_FOUND)
    }

    // ─── Scheduled tasks ─────────────────────────────────────────────────────

    private fun listScheduledTasks(req: Request): Response {
        val svc = scheduledTaskService ?: return Response(Status.SERVICE_UNAVAILABLE)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                org.booktower.config.Json.mapper
                    .writeValueAsString(svc.list()),
            )
    }

    private fun createScheduledTask(req: Request): Response {
        val svc = scheduledTaskService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val request =
            runCatching {
                org.booktower.config.Json.mapper
                    .readValue(req.bodyString(), CreateScheduledTaskRequest::class.java)
            }.getOrNull() ?: return Response(Status.BAD_REQUEST).body("""{"error":"Invalid request body"}""")
        return runCatching { svc.create(request) }
            .fold(
                onSuccess = {
                    Response(
                        Status.CREATED,
                    ).header("Content-Type", "application/json").body(
                        org.booktower.config.Json.mapper
                            .writeValueAsString(it),
                    )
                },
                onFailure = { e ->
                    Response(Status.BAD_REQUEST).header("Content-Type", "application/json").body("""{"error":"${e.message}"}""")
                },
            )
    }

    private fun updateScheduledTask(req: Request): Response {
        val svc = scheduledTaskService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val id =
            req.uri.path
                .split("/")
                .lastOrNull() ?: return Response(Status.BAD_REQUEST)
        val request =
            runCatching {
                org.booktower.config.Json.mapper
                    .readValue(req.bodyString(), UpdateScheduledTaskRequest::class.java)
            }.getOrNull() ?: return Response(Status.BAD_REQUEST).body("""{"error":"Invalid request body"}""")
        val updated =
            runCatching { svc.update(id, request) }
                .getOrElse { e ->
                    return Response(Status.BAD_REQUEST).header("Content-Type", "application/json").body("""{"error":"${e.message}"}""")
                }
                ?: return Response(Status.NOT_FOUND)
        return Response(
            Status.OK,
        ).header("Content-Type", "application/json").body(
            org.booktower.config.Json.mapper
                .writeValueAsString(updated),
        )
    }

    private fun deleteScheduledTask(req: Request): Response {
        val svc = scheduledTaskService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val id =
            req.uri.path
                .split("/")
                .lastOrNull() ?: return Response(Status.BAD_REQUEST)
        return if (svc.delete(id)) Response(Status.NO_CONTENT) else Response(Status.NOT_FOUND)
    }

    private fun triggerScheduledTask(req: Request): Response {
        val svc = scheduledTaskService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val parts = req.uri.path.split("/")
        val id = parts.dropLast(1).lastOrNull() ?: return Response(Status.BAD_REQUEST)
        val historyId = svc.triggerNow(id) ?: return Response(Status.NOT_FOUND)
        return Response(Status.OK).header("Content-Type", "application/json").body("""{"historyId":"$historyId"}""")
    }

    private fun scheduledTaskHistory(req: Request): Response {
        val svc = scheduledTaskService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val parts = req.uri.path.split("/")
        val id = parts.dropLast(1).lastOrNull() ?: return Response(Status.BAD_REQUEST)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                org.booktower.config.Json.mapper
                    .writeValueAsString(svc.getHistory(id)),
            )
    }

    private fun bulkRegenerateCovers(req: Request): Response {
        val svc = bulkCoverService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val userId = AuthenticatedUser.from(req)
        val libraryId = req.query("libraryId")
        val result = svc.regenerateCovers(userId, libraryId)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                org.booktower.config.Json.mapper
                    .writeValueAsString(result),
            )
    }

    private fun bulkMetadataRefresh(req: Request): Response {
        val svc = bulkMetadataRefreshService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val userId = AuthenticatedUser.from(req)
        val libraryId = req.query("libraryId")
        val result = svc.refresh(userId, libraryId)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                org.booktower.config.Json.mapper
                    .writeValueAsString(result),
            )
    }

    private fun telemetryStats(req: Request): Response {
        val svc = telemetryService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val stats = svc.getStats()
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                org.booktower.config.Json.mapper
                    .writeValueAsString(stats),
            )
    }

    // ─── Backup / restore ─────────────────────────────────────────────────

    private fun exportBackup(req: Request): Response {
        val svc = backupService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val json = svc.export()
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .header("Content-Disposition", "attachment; filename=\"booktower-backup.json\"")
            .body(json)
    }

    private fun importBackup(req: Request): Response {
        val svc = backupService ?: return Response(Status.SERVICE_UNAVAILABLE)
        return try {
            val metadata = svc.import(req.body.stream)
            Response(Status.OK)
                .header("Content-Type", "application/json")
                .body(
                    org.booktower.config.Json.mapper
                        .writeValueAsString(metadata),
                )
        } catch (e: Exception) {
            Response(Status.BAD_REQUEST)
                .header("Content-Type", "application/json")
                .body("""{"error":"${e.message}"}""")
        }
    }

    // ─── Batch import ─────────────────────────────────────────────────────

    private fun batchImportDirectory(req: Request): Response {
        val svc = batchImportService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val userId = AuthenticatedUser.from(req)
        val body =
            runCatching {
                org.booktower.config.Json.mapper
                    .readTree(req.bodyString())
            }.getOrNull()
                ?: return Response(Status.BAD_REQUEST)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"Invalid request body"}""")
        val directoryPath =
            body.get("path")?.asText()?.takeIf { it.isNotBlank() }
                ?: return Response(Status.BAD_REQUEST)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"path is required"}""")
        val libraryId =
            body.get("libraryId")?.asText()?.takeIf { it.isNotBlank() }
                ?: return Response(Status.BAD_REQUEST)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"libraryId is required"}""")
        val config =
            org.booktower.services.BatchImportConfig(
                maxConcurrency = body.get("maxConcurrency")?.asInt() ?: 2,
                throttleMs = body.get("throttleMs")?.asLong() ?: 500,
                maxFiles = body.get("maxFiles")?.asInt() ?: 1000,
                maxFileSizeMb = body.get("maxFileSizeMb")?.asInt() ?: 500,
            )
        val result = svc.importDirectory(userId, libraryId, directoryPath, config)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                org.booktower.config.Json.mapper
                    .writeValueAsString(result),
            )
    }
}
