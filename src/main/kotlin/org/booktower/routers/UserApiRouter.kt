package org.booktower.routers

import org.booktower.filters.AuthenticatedUser
import org.booktower.handlers.ApiTokenHandler
import org.booktower.handlers.BackgroundTaskHandler
import org.booktower.handlers.ExportHandler
import org.booktower.handlers.GoodreadsImportHandler
import org.booktower.handlers.UserSettingsHandler
import org.booktower.services.BookDeliveryService
import org.booktower.services.BookService
import org.booktower.services.CollectionService
import org.booktower.services.ContentRestrictionsService
import org.booktower.services.CreateCollectionRequest
import org.booktower.services.FilterPresetService
import org.booktower.services.HardcoverSyncService
import org.booktower.services.NotificationService
import org.booktower.services.OpdsCredentialsService
import org.booktower.services.ReadingStatsService
import org.booktower.services.SaveFilterPresetRequest
import org.booktower.services.TelemetryService
import org.booktower.services.UserSettingsService
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.body.form
import org.http4k.core.then
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind

class UserApiRouter(
    private val filters: FilterSet,
    private val settingsHandler: UserSettingsHandler,
    private val bookService: BookService,
    private val userSettingsService: UserSettingsService,
    private val readingStatsService: ReadingStatsService?,
    private val hardcoverSyncService: HardcoverSyncService?,
    private val opdsCredentialsService: OpdsCredentialsService?,
    private val contentRestrictionsService: ContentRestrictionsService?,
    private val filterPresetService: FilterPresetService?,
    private val telemetryService: TelemetryService?,
    private val bookDeliveryService: BookDeliveryService?,
    private val notificationService: NotificationService?,
    private val backgroundTaskHandler: BackgroundTaskHandler?,
    private val apiTokenHandler: ApiTokenHandler,
    private val exportHandler: ExportHandler,
    private val goodreadsImportHandler: GoodreadsImportHandler,
    private val collectionService: CollectionService? = null,
) {
    @Suppress("LongMethod")
    fun routes(): List<RoutingHttpHandler> =
        listOf(
            "/api/settings" bind Method.GET to filters.auth.then(settingsHandler::getAll),
            "/api/settings/{key}" bind Method.PUT to filters.auth.then(settingsHandler::set),
            "/api/settings/{key}" bind Method.DELETE to filters.auth.then(settingsHandler::delete),
            // OPDS separate credentials
            "/api/user/opds-credentials" bind Method.GET to filters.auth.then(::getOpdsCredentials),
            "/api/user/opds-credentials" bind Method.PUT to filters.auth.then(::setOpdsCredentials),
            "/api/user/opds-credentials" bind Method.DELETE to filters.auth.then(::clearOpdsCredentials),
            // Hardcover.app sync
            "/api/user/hardcover/key" bind Method.PUT to filters.auth.then(::setHardcoverKey),
            "/api/user/hardcover/status" bind Method.GET to filters.auth.then(::hardcoverStatus),
            "/api/books/{id}/hardcover/sync" bind Method.POST to filters.auth.then(::syncBookToHardcover),
            "/api/books/{id}/hardcover/mapping" bind Method.GET to filters.auth.then(::getHardcoverMapping),
            // Content restrictions
            "/api/user/content-restrictions" bind Method.GET to filters.auth.then(::getContentRestrictions),
            "/api/user/content-restrictions" bind Method.PUT to filters.auth.then(::updateContentRestrictions),
            // Telemetry
            "/api/telemetry/status" bind Method.GET to filters.auth.then(::telemetryStatus),
            "/api/telemetry/opt-in" bind Method.POST to filters.auth.then(::telemetryOptIn),
            "/api/telemetry/opt-out" bind Method.POST to filters.auth.then(::telemetryOptOut),
            // First-run setup wizard
            "/api/setup/status" bind Method.GET to filters.auth.then(::setupStatus),
            "/api/setup/complete" bind Method.POST to filters.auth.then(::completeSetup),
            "/api/setup/steps/{step}/complete" bind Method.POST to filters.auth.then(::completeSetupStep),
            // Browse mode preference
            "/api/user/preferences/browse-mode" bind Method.GET to filters.auth.then(::getBrowseMode),
            "/api/user/preferences/browse-mode" bind Method.PUT to filters.auth.then(::setBrowseMode),
            // Filter presets
            "/api/user/filter-presets" bind Method.GET to filters.auth.then(::listFilterPresets),
            "/api/user/filter-presets" bind Method.POST to filters.auth.then(::createFilterPreset),
            "/api/user/filter-presets/{id}" bind Method.GET to filters.auth.then(::getFilterPreset),
            "/api/user/filter-presets/{id}" bind Method.PUT to filters.auth.then(::updateFilterPreset),
            "/api/user/filter-presets/{id}" bind Method.DELETE to filters.auth.then(::deleteFilterPreset),
            // Reading statistics
            "/api/stats/reading" bind Method.GET to filters.auth.then(::getReadingStats),
            // Notifications
            "/api/notifications" bind Method.GET to filters.auth.then(::listNotifications),
            "/api/notifications/count" bind Method.GET to filters.auth.then(::getNotificationCount),
            "/api/notifications/stream" bind Method.GET to filters.auth.then(::streamNotifications),
            "/api/notifications/read-all" bind Method.POST to filters.auth.then(::markAllNotificationsRead),
            "/api/notifications/{id}/read" bind Method.POST to filters.auth.then(::markNotificationRead),
            "/api/notifications/{id}" bind Method.DELETE to filters.auth.then(::deleteNotification),
            // API tokens
            "/api/tokens" bind Method.GET to filters.auth.then(apiTokenHandler::list),
            "/api/tokens" bind Method.POST to filters.auth.then(apiTokenHandler::create),
            "/api/tokens/{id}" bind Method.DELETE to filters.auth.then(apiTokenHandler::revoke),
            // Export & Import
            "/api/export" bind Method.GET to filters.auth.then(exportHandler::export),
            "/api/import/goodreads" bind Method.POST to filters.auth.then(goodreadsImportHandler::import),
            // Background tasks (user-level)
            "/api/tasks" bind Method.GET to
                filters.auth.then(optionalHandler(backgroundTaskHandler?.let { it::list })),
            "/api/tasks/{id}" bind Method.DELETE to
                filters.auth.then(optionalHandler(backgroundTaskHandler?.let { it::dismiss })),
            "/api/tasks/{id}/retry" bind Method.POST to
                filters.auth.then(optionalHandler(backgroundTaskHandler?.let { it::retry })),
            // Collections
            "/api/collections" bind Method.GET to filters.auth.then(::listCollections),
            "/api/collections" bind Method.POST to filters.auth.then(::createCollection),
            "/api/collections/{id}" bind Method.DELETE to filters.auth.then(::deleteCollection),
            "/api/collections/{id}/books/{bookId}" bind Method.POST to filters.auth.then(::addBookToCollection),
            "/api/collections/{id}/books/{bookId}" bind Method.DELETE to filters.auth.then(::removeBookFromCollection),
        )

    // ─── Collections ────────────────────────────────────────────────────────

    private fun listCollections(req: Request): Response {
        val svc = collectionService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val userId = AuthenticatedUser.from(req)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                org.booktower.config.Json.mapper
                    .writeValueAsString(svc.getCollections(userId)),
            )
    }

    private fun createCollection(req: Request): Response {
        val svc = collectionService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val userId = AuthenticatedUser.from(req)
        val name = req.form("name")?.trim() ?: return Response(Status.BAD_REQUEST)
        if (name.isBlank() || name.length > 100) return Response(Status.BAD_REQUEST)
        val desc = req.form("description")?.trim()?.takeIf { it.isNotBlank() }
        val collection = svc.createCollection(userId, CreateCollectionRequest(name, desc))
        return Response(Status.CREATED)
            .header("Content-Type", "application/json")
            .body(
                org.booktower.config.Json.mapper
                    .writeValueAsString(collection),
            )
    }

    private fun deleteCollection(req: Request): Response {
        val svc = collectionService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val userId = AuthenticatedUser.from(req)
        val id =
            req.uri.path
                .split("/")
                .lastOrNull()
                ?.let { runCatching { java.util.UUID.fromString(it) }.getOrNull() }
                ?: return Response(Status.BAD_REQUEST)
        return if (svc.deleteCollection(userId, id)) Response(Status.NO_CONTENT) else Response(Status.NOT_FOUND)
    }

    private fun addBookToCollection(req: Request): Response {
        val svc = collectionService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val userId = AuthenticatedUser.from(req)
        val parts = req.uri.path.split("/")
        val collId = parts.getOrNull(3)?.let { runCatching { java.util.UUID.fromString(it) }.getOrNull() } ?: return Response(Status.BAD_REQUEST)
        val bookId = parts.getOrNull(5)?.let { runCatching { java.util.UUID.fromString(it) }.getOrNull() } ?: return Response(Status.BAD_REQUEST)
        return if (svc.addBook(userId, collId, bookId)) Response(Status.OK) else Response(Status.NOT_FOUND)
    }

    private fun removeBookFromCollection(req: Request): Response {
        val svc = collectionService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val userId = AuthenticatedUser.from(req)
        val parts = req.uri.path.split("/")
        val collId = parts.getOrNull(3)?.let { runCatching { java.util.UUID.fromString(it) }.getOrNull() } ?: return Response(Status.BAD_REQUEST)
        val bookId = parts.getOrNull(5)?.let { runCatching { java.util.UUID.fromString(it) }.getOrNull() } ?: return Response(Status.BAD_REQUEST)
        return if (svc.removeBook(userId, collId, bookId)) Response(Status.NO_CONTENT) else Response(Status.NOT_FOUND)
    }

    // ─── Inline handler methods ──────────────────────────────────────────────

    private fun getOpdsCredentials(req: Request): Response {
        val svc = opdsCredentialsService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val userId = AuthenticatedUser.from(req)
        val info = svc.getCredentials(userId)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                org.booktower.config.Json.mapper.writeValueAsString(
                    mapOf("configured" to (info != null), "opdsUsername" to info?.opdsUsername),
                ),
            )
    }

    private fun setOpdsCredentials(req: Request): Response {
        val svc = opdsCredentialsService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val userId = AuthenticatedUser.from(req)
        val body =
            runCatching {
                org.booktower.config.Json.mapper
                    .readTree(req.bodyString())
            }.getOrNull()
        val opdsUsername =
            body?.get("opdsUsername")?.asText()?.takeIf { it.isNotBlank() }
                ?: return Response(Status.BAD_REQUEST)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"opdsUsername required"}""")
        val password =
            body.get("password")?.asText()?.takeIf { it.isNotBlank() }
                ?: return Response(Status.BAD_REQUEST)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"password required"}""")
        return try {
            svc.setCredentials(userId, opdsUsername, password)
            Response(Status.NO_CONTENT)
        } catch (e: IllegalArgumentException) {
            Response(Status.BAD_REQUEST)
                .header("Content-Type", "application/json")
                .body("""{"error":"${e.message}"}""")
        }
    }

    private fun clearOpdsCredentials(req: Request): Response {
        val svc = opdsCredentialsService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val userId = AuthenticatedUser.from(req)
        svc.clearCredentials(userId)
        return Response(Status.NO_CONTENT)
    }

    private fun setHardcoverKey(req: Request): Response {
        val svc = hardcoverSyncService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val userId = AuthenticatedUser.from(req)
        val body =
            runCatching {
                org.booktower.config.Json.mapper
                    .readTree(req.bodyString())
            }.getOrNull()
        val apiKey = body?.get("apiKey")?.asText() ?: ""
        svc.setApiKey(userId, apiKey)
        return Response(Status.NO_CONTENT)
    }

    private fun hardcoverStatus(req: Request): Response {
        val svc = hardcoverSyncService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val userId = AuthenticatedUser.from(req)
        val hasKey = svc.hasApiKey(userId)
        if (!hasKey) {
            return Response(Status.OK)
                .header("Content-Type", "application/json")
                .body("""{"configured":false,"connected":false}""")
        }
        val username = svc.testConnection(userId)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                org.booktower.config.Json.mapper.writeValueAsString(
                    mapOf("configured" to true, "connected" to (username != null), "username" to username),
                ),
            )
    }

    private fun syncBookToHardcover(req: Request): Response {
        val svc = hardcoverSyncService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val userId = AuthenticatedUser.from(req)
        val bookId =
            extractBookIdFromPath(req)
                ?: return Response(Status.BAD_REQUEST).header("Content-Type", "application/json").body("""{"error":"Invalid book ID"}""")
        val book =
            bookService.getBook(userId, bookId)
                ?: return Response(Status.NOT_FOUND).header("Content-Type", "application/json").body("""{"error":"Book not found"}""")
        val body =
            runCatching {
                org.booktower.config.Json.mapper
                    .readTree(req.bodyString())
            }.getOrNull()
        val status = body?.get("status")?.asText()?.takeIf { it.isNotBlank() }
        val currentPage = body?.get("currentPage")?.asInt()
        val result =
            if (currentPage != null) {
                svc.syncProgress(userId, bookId, book.isbn, book.title, book.author, currentPage)
            } else if (status != null) {
                svc.syncBookStatus(userId, bookId, book.isbn, book.title, book.author, status)
            } else {
                return Response(Status.BAD_REQUEST)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"Provide status or currentPage"}""")
            }
        val code = if (result.synced) Status.OK else Status.BAD_GATEWAY
        return Response(code)
            .header("Content-Type", "application/json")
            .body(
                org.booktower.config.Json.mapper
                    .writeValueAsString(result),
            )
    }

    private fun getHardcoverMapping(req: Request): Response {
        val svc = hardcoverSyncService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val userId = AuthenticatedUser.from(req)
        val bookId =
            extractBookIdFromPath(req)
                ?: return Response(Status.BAD_REQUEST).header("Content-Type", "application/json").body("""{"error":"Invalid book ID"}""")
        val mapping =
            svc.getMapping(userId, bookId)
                ?: return Response(Status.NOT_FOUND).header("Content-Type", "application/json").body("""{"error":"No mapping found"}""")
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                org.booktower.config.Json.mapper
                    .writeValueAsString(mapping),
            )
    }

    private fun getContentRestrictions(req: Request): Response {
        val svc = contentRestrictionsService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val userId = AuthenticatedUser.from(req)
        val restrictions = svc.get(userId)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                org.booktower.config.Json.mapper
                    .writeValueAsString(restrictions),
            )
    }

    private fun updateContentRestrictions(req: Request): Response {
        val svc = contentRestrictionsService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val userId = AuthenticatedUser.from(req)
        val body =
            runCatching {
                org.booktower.config.Json.mapper
                    .readTree(req.bodyString())
            }.getOrNull()
                ?: return Response(Status.BAD_REQUEST).body("""{"error":"Invalid JSON"}""")

        val maxRating = if (body.has("maxAgeRating") && !body.get("maxAgeRating").isNull) body.get("maxAgeRating").asText() else null
        val blockedTags =
            if (body.has("blockedTags")) {
                val node = body.get("blockedTags")
                if (node.isArray) (0 until node.size()).map { node[it].asText() } else emptyList()
            } else {
                null
            }

        return runCatching {
            if (body.has("maxAgeRating")) svc.setMaxAgeRating(userId, maxRating)
            if (blockedTags != null) svc.setBlockedTags(userId, blockedTags)
            svc.get(userId)
        }.fold(
            onSuccess = { r ->
                Response(Status.OK)
                    .header("Content-Type", "application/json")
                    .body(
                        org.booktower.config.Json.mapper
                            .writeValueAsString(r),
                    )
            },
            onFailure = { e ->
                Response(Status.BAD_REQUEST)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"${e.message}"}""")
            },
        )
    }

    private fun telemetryStatus(req: Request): Response {
        val svc = telemetryService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val userId = AuthenticatedUser.from(req)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                org.booktower.config.Json.mapper
                    .writeValueAsString(mapOf("optedIn" to svc.isOptedIn(userId))),
            )
    }

    private fun telemetryOptIn(req: Request): Response {
        val svc = telemetryService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val userId = AuthenticatedUser.from(req)
        svc.optIn(userId)
        return Response(Status.OK).header("Content-Type", "application/json").body("""{"optedIn":true}""")
    }

    private fun telemetryOptOut(req: Request): Response {
        val svc = telemetryService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val userId = AuthenticatedUser.from(req)
        svc.optOut(userId)
        return Response(Status.OK).header("Content-Type", "application/json").body("""{"optedIn":false}""")
    }

    // ─── First-run setup wizard ───────────────────────────────────────────────

    private val setupSteps = listOf("profile", "library", "import", "preferences")
    private val setupCompletedKey = "setup.wizard.completed"

    private fun setupStepKey(step: String) = "setup.wizard.$step"

    private fun setupStatus(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val completed = userSettingsService.get(userId, setupCompletedKey) == "true"
        val stepStatus =
            setupSteps.associate { step ->
                step to (userSettingsService.get(userId, setupStepKey(step)) == "true")
            }
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                org.booktower.config.Json.mapper.writeValueAsString(
                    mapOf("completed" to completed, "steps" to stepStatus),
                ),
            )
    }

    private fun completeSetupStep(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val step =
            req.uri.path
                .split("/")
                .dropLast(1)
                .lastOrNull()
                ?: return Response(Status.BAD_REQUEST)
        if (step !in setupSteps) {
            return Response(Status.BAD_REQUEST)
                .header("Content-Type", "application/json")
                .body("""{"error":"Unknown step '$step'. Valid steps: ${setupSteps.joinToString()}"}""")
        }
        userSettingsService.set(userId, setupStepKey(step), "true")
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body("""{"step":"$step","completed":true}""")
    }

    private fun completeSetup(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        userSettingsService.set(userId, setupCompletedKey, "true")
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body("""{"completed":true}""")
    }

    // ─── Browse mode preference ───────────────────────────────────────────────

    private val validBrowseModes = setOf("grid", "list", "table")
    private val browseModeKey = "browse.mode"

    private fun getBrowseMode(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val mode = userSettingsService.get(userId, browseModeKey) ?: "grid"
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                org.booktower.config.Json.mapper
                    .writeValueAsString(mapOf("browseMode" to mode)),
            )
    }

    private fun setBrowseMode(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val body =
            runCatching {
                org.booktower.config.Json.mapper
                    .readTree(req.bodyString())
            }.getOrNull()
                ?: return Response(Status.BAD_REQUEST)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"Invalid JSON"}""")
        val mode = body.get("browseMode")?.asText()
        if (mode.isNullOrBlank() || mode !in validBrowseModes) {
            return Response(Status.BAD_REQUEST)
                .header("Content-Type", "application/json")
                .body("""{"error":"Invalid browseMode. Must be one of: ${validBrowseModes.joinToString()}"}""")
        }
        userSettingsService.set(userId, browseModeKey, mode)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                org.booktower.config.Json.mapper
                    .writeValueAsString(mapOf("browseMode" to mode)),
            )
    }

    // ─── Filter presets ───────────────────────────────────────────────────────

    private fun listFilterPresets(req: Request): Response {
        val svc = filterPresetService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val userId = AuthenticatedUser.from(req)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                org.booktower.config.Json.mapper
                    .writeValueAsString(svc.list(userId)),
            )
    }

    private fun createFilterPreset(req: Request): Response {
        val svc = filterPresetService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val userId = AuthenticatedUser.from(req)
        val request =
            runCatching {
                org.booktower.config.Json.mapper
                    .readValue(req.bodyString(), SaveFilterPresetRequest::class.java)
            }.getOrNull() ?: return Response(Status.BAD_REQUEST)
                .header("Content-Type", "application/json")
                .body("""{"error":"Invalid request body"}""")
        val result =
            runCatching { svc.create(userId, request) }
                .getOrElse { e ->
                    return Response(Status.BAD_REQUEST).header("Content-Type", "application/json").body("""{"error":"${e.message}"}""")
                }
        return Response(Status.CREATED)
            .header("Content-Type", "application/json")
            .body(
                org.booktower.config.Json.mapper
                    .writeValueAsString(result),
            )
    }

    private fun getFilterPreset(req: Request): Response {
        val svc = filterPresetService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val userId = AuthenticatedUser.from(req)
        val presetId =
            req.uri.path
                .split("/")
                .lastOrNull() ?: return Response(Status.BAD_REQUEST)
        val preset = svc.get(userId, presetId) ?: return Response(Status.NOT_FOUND)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                org.booktower.config.Json.mapper
                    .writeValueAsString(preset),
            )
    }

    private fun updateFilterPreset(req: Request): Response {
        val svc = filterPresetService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val userId = AuthenticatedUser.from(req)
        val presetId =
            req.uri.path
                .split("/")
                .lastOrNull() ?: return Response(Status.BAD_REQUEST)
        val request =
            runCatching {
                org.booktower.config.Json.mapper
                    .readValue(req.bodyString(), SaveFilterPresetRequest::class.java)
            }.getOrNull() ?: return Response(Status.BAD_REQUEST)
                .header("Content-Type", "application/json")
                .body("""{"error":"Invalid request body"}""")
        val result =
            runCatching { svc.update(userId, presetId, request) }
                .getOrElse { e ->
                    return Response(Status.BAD_REQUEST).header("Content-Type", "application/json").body("""{"error":"${e.message}"}""")
                }
                ?: return Response(Status.NOT_FOUND)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                org.booktower.config.Json.mapper
                    .writeValueAsString(result),
            )
    }

    private fun deleteFilterPreset(req: Request): Response {
        val svc = filterPresetService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val userId = AuthenticatedUser.from(req)
        val presetId =
            req.uri.path
                .split("/")
                .lastOrNull() ?: return Response(Status.BAD_REQUEST)
        return if (svc.delete(userId, presetId)) Response(Status.NO_CONTENT) else Response(Status.NOT_FOUND)
    }

    // ─── Reading statistics ───────────────────────────────────────────────────

    private fun getReadingStats(req: Request): Response {
        val svc = readingStatsService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val userId = AuthenticatedUser.from(req)
        val days = req.query("days")?.toIntOrNull()?.coerceIn(7, 365) ?: 365
        val stats = svc.getStats(userId, days)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                org.booktower.config.Json.mapper
                    .writeValueAsString(stats),
            )
    }

    // ─── Notifications ────────────────────────────────────────────────────────

    private fun listNotifications(req: Request): Response {
        val svc = notificationService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val userId = AuthenticatedUser.from(req)
        val unreadOnly = req.uri.query.contains("unread=true")
        val items = svc.list(userId, unreadOnly)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                org.booktower.config.Json.mapper
                    .writeValueAsString(items),
            )
    }

    private fun getNotificationCount(req: Request): Response {
        val svc = notificationService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val userId = AuthenticatedUser.from(req)
        val count = svc.unreadCount(userId)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body("""{"count":$count}""")
    }

    companion object {
        private const val SSE_HEARTBEAT_SECONDS = 15L
    }

    private fun streamNotifications(req: Request): Response {
        val svc = notificationService ?: return Response(Status.SERVICE_UNAVAILABLE)
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
                        val items = svc.list(userId, unreadOnly = true)
                        for (item in items) {
                            write("event: notification\ndata: ${org.booktower.config.Json.mapper.writeValueAsString(item)}\n\n")
                        }
                        write("event: heartbeat\ndata: {}\n\n")

                        // Keep alive: heartbeat + check for new notifications
                        var lastCount = items.size
                        while (!Thread.currentThread().isInterrupted) {
                            Thread.sleep(SSE_HEARTBEAT_SECONDS * 1000)
                            val current = svc.list(userId, unreadOnly = true)
                            if (current.size > lastCount) {
                                current.take(current.size - lastCount).forEach { item ->
                                    write("event: notification\ndata: ${org.booktower.config.Json.mapper.writeValueAsString(item)}\n\n")
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

    private fun markNotificationRead(req: Request): Response {
        val svc = notificationService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val userId = AuthenticatedUser.from(req)
        val notificationId =
            req.uri.path
                .split("/")
                .dropLast(1)
                .lastOrNull()
                ?: return Response(Status.BAD_REQUEST)
        return if (svc.markRead(userId, notificationId)) {
            Response(Status.OK).header("Content-Type", "application/json").body("{}")
        } else {
            Response(Status.NOT_FOUND)
        }
    }

    private fun markAllNotificationsRead(req: Request): Response {
        val svc = notificationService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val userId = AuthenticatedUser.from(req)
        val count = svc.markAllRead(userId)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                org.booktower.config.Json.mapper
                    .writeValueAsString(mapOf("marked" to count)),
            )
    }

    private fun deleteNotification(req: Request): Response {
        val svc = notificationService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val userId = AuthenticatedUser.from(req)
        val notificationId =
            req.uri.path
                .split("/")
                .lastOrNull()
                ?: return Response(Status.BAD_REQUEST)
        return if (svc.delete(userId, notificationId)) Response(Status.NO_CONTENT) else Response(Status.NOT_FOUND)
    }
}
