package org.runary.routers

import org.runary.filters.AuthenticatedUser
import org.runary.handlers.ApiTokenHandler
import org.runary.handlers.BackgroundTaskHandler
import org.runary.handlers.CollectionApiHandler
import org.runary.handlers.CustomFieldApiHandler
import org.runary.handlers.ExportHandler
import org.runary.handlers.GoodreadsImportHandler
import org.runary.handlers.NotificationApiHandler
import org.runary.handlers.ReadingListApiHandler
import org.runary.handlers.UserSettingsHandler
import org.runary.handlers.WebhookApiHandler
import org.runary.handlers.WishlistApiHandler
import org.runary.services.BookDeliveryService
import org.runary.services.BookService
import org.runary.services.ContentRestrictionsService
import org.runary.services.FilterPresetService
import org.runary.services.HardcoverSyncService
import org.runary.services.OpdsCredentialsService
import org.runary.services.ReadingStatsService
import org.runary.services.SaveFilterPresetRequest
import org.runary.services.TelemetryService
import org.runary.services.UserSettingsService
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
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
    private val backgroundTaskHandler: BackgroundTaskHandler?,
    private val apiTokenHandler: ApiTokenHandler,
    private val exportHandler: ExportHandler,
    private val goodreadsImportHandler: GoodreadsImportHandler,
    private val collectionApiHandler: CollectionApiHandler? = null,
    private val auditService: org.runary.services.AuditService? = null,
    private val libraryStatsService: org.runary.services.LibraryStatsService? = null,
    private val webhookApiHandler: WebhookApiHandler? = null,
    private val readingTimelineService: org.runary.services.ReadingTimelineService? = null,
    private val readingGoalService: org.runary.services.ReadingGoalService? = null,
    private val annotationExportService: org.runary.services.AnnotationExportService? = null,
    private val discoveryService: org.runary.services.DiscoveryService? = null,
    private val positionSyncService: org.runary.services.PositionSyncService? = null,
    private val customFieldApiHandler: CustomFieldApiHandler? = null,
    private val publicProfileService: org.runary.services.PublicProfileService? = null,
    private val readingSpeedService: org.runary.services.ReadingSpeedService? = null,
    private val bookConditionService: org.runary.services.BookConditionService? = null,
    private val readingListApiHandler: ReadingListApiHandler? = null,
    private val annotationService: org.runary.services.AnnotationService? = null,
    private val wishlistApiHandler: WishlistApiHandler? = null,
    private val advancedSearchService: org.runary.services.AdvancedSearchService? = null,
    private val notificationApiHandler: NotificationApiHandler? = null,
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
            "/api/notifications" bind Method.GET to filters.auth.then(optionalHandler(notificationApiHandler?.let { it::listNotifications })),
            "/api/notifications/count" bind Method.GET to filters.auth.then(optionalHandler(notificationApiHandler?.let { it::getNotificationCount })),
            "/api/notifications/stream" bind Method.GET to filters.auth.then(optionalHandler(notificationApiHandler?.let { it::streamNotifications })),
            "/api/notifications/read-all" bind Method.POST to filters.auth.then(optionalHandler(notificationApiHandler?.let { it::markAllNotificationsRead })),
            "/api/notifications/{id}/read" bind Method.POST to filters.auth.then(optionalHandler(notificationApiHandler?.let { it::markNotificationRead })),
            "/api/notifications/{id}" bind Method.DELETE to filters.auth.then(optionalHandler(notificationApiHandler?.let { it::deleteNotification })),
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
            // Activity / audit log (user's own events)
            "/api/activity" bind Method.GET to filters.auth.then(::listActivity),
            // Collections
            "/api/collections" bind Method.GET to filters.auth.then(optionalHandler(collectionApiHandler?.let { it::listCollections })),
            "/api/collections" bind Method.POST to filters.auth.then(optionalHandler(collectionApiHandler?.let { it::createCollection })),
            "/api/collections/{id}" bind Method.DELETE to filters.auth.then(optionalHandler(collectionApiHandler?.let { it::deleteCollection })),
            "/api/collections/{id}/books/{bookId}" bind Method.POST to filters.auth.then(optionalHandler(collectionApiHandler?.let { it::addBookToCollection })),
            "/api/collections/{id}/books/{bookId}" bind Method.DELETE to filters.auth.then(optionalHandler(collectionApiHandler?.let { it::removeBookFromCollection })),
            "/api/stats/library" bind Method.GET to filters.auth.then(::getLibraryStats),
            "/api/timeline" bind Method.GET to filters.auth.then(::getReadingTimeline),
            "/api/webhooks" bind Method.GET to filters.auth.then(optionalHandler(webhookApiHandler?.let { it::listWebhooks })),
            "/api/webhooks" bind Method.POST to filters.auth.then(optionalHandler(webhookApiHandler?.let { it::createWebhook })),
            "/api/webhooks/{id}" bind Method.DELETE to filters.auth.then(optionalHandler(webhookApiHandler?.let { it::deleteWebhook })),
            "/api/webhooks/{id}/toggle" bind Method.POST to filters.auth.then(optionalHandler(webhookApiHandler?.let { it::toggleWebhook })),
            "/api/goals/reading" bind Method.GET to filters.auth.then(::getReadingGoal),
            "/api/goals/reading" bind Method.PUT to filters.auth.then(::setReadingGoal),
            "/api/export/annotations" bind Method.GET to filters.auth.then(::exportAnnotations),
            "/api/export/annotations/markdown" bind Method.GET to filters.auth.then(::exportAnnotationsMarkdown),
            "/api/export/annotations/readwise" bind Method.GET to filters.auth.then(::exportAnnotationsReadwise),
            "/api/discover" bind Method.GET to filters.auth.then(::discover),
            "/api/books/{bookId}/position" bind Method.GET to filters.auth.then(::pullPosition),
            "/api/books/{bookId}/position" bind Method.PUT to filters.auth.then(::pushPosition),
            // Custom metadata fields
            "/api/custom-fields" bind Method.GET to filters.auth.then(optionalHandler(customFieldApiHandler?.let { it::listFieldDefinitions })),
            "/api/custom-fields" bind Method.POST to filters.auth.then(optionalHandler(customFieldApiHandler?.let { it::createFieldDefinition })),
            "/api/custom-fields/{id}" bind Method.DELETE to filters.auth.then(optionalHandler(customFieldApiHandler?.let { it::deleteFieldDefinition })),
            "/api/books/{bookId}/custom-fields" bind Method.GET to filters.auth.then(optionalHandler(customFieldApiHandler?.let { it::getBookCustomFields })),
            "/api/books/{bookId}/custom-fields" bind Method.PUT to filters.auth.then(optionalHandler(customFieldApiHandler?.let { it::setBookCustomField })),
            "/api/books/{bookId}/custom-fields/{fieldName}" bind Method.DELETE to filters.auth.then(optionalHandler(customFieldApiHandler?.let { it::deleteBookCustomField })),
            // Public profile
            "/api/user/profile/public" bind Method.GET to filters.auth.then(::getProfilePublicStatus),
            "/api/user/profile/public" bind Method.PUT to filters.auth.then(::setProfilePublic),
            "/api/profile/{username}" bind Method.GET to ::getPublicProfile,
            // Reading speed analytics
            "/api/stats/speed" bind Method.GET to filters.auth.then(::getReadingSpeed),
            // Reading streaks widget
            "/api/stats/streaks" bind Method.GET to filters.auth.then(::getStreaks),
            // Book condition tracker
            "/api/books/{bookId}/condition" bind Method.GET to filters.auth.then(::getBookCondition),
            "/api/books/{bookId}/condition" bind Method.PUT to filters.auth.then(::updateBookCondition),
            // Reading lists
            "/api/reading-lists" bind Method.GET to filters.auth.then(optionalHandler(readingListApiHandler?.let { it::listReadingLists })),
            "/api/reading-lists" bind Method.POST to filters.auth.then(optionalHandler(readingListApiHandler?.let { it::createReadingList })),
            "/api/reading-lists/{id}" bind Method.GET to filters.auth.then(optionalHandler(readingListApiHandler?.let { it::getReadingList })),
            "/api/reading-lists/{id}" bind Method.DELETE to filters.auth.then(optionalHandler(readingListApiHandler?.let { it::deleteReadingList })),
            "/api/reading-lists/{id}/books/{bookId}" bind Method.POST to filters.auth.then(optionalHandler(readingListApiHandler?.let { it::addBookToReadingList })),
            "/api/reading-lists/{id}/books/{bookId}" bind Method.DELETE to filters.auth.then(optionalHandler(readingListApiHandler?.let { it::removeBookFromReadingList })),
            "/api/reading-lists/{id}/books/{bookId}/toggle" bind Method.POST to filters.auth.then(optionalHandler(readingListApiHandler?.let { it::toggleReadingListItem })),
            "/api/reading-lists/{id}/reorder" bind Method.POST to filters.auth.then(optionalHandler(readingListApiHandler?.let { it::reorderReadingList })),
            // Shared annotations
            "/api/annotations/{id}/share" bind Method.POST to filters.auth.then(::shareAnnotation),
            "/api/books/{bookId}/shared-annotations" bind Method.GET to filters.auth.then(::getSharedAnnotations),
            // Wishlist
            "/api/wishlist" bind Method.GET to filters.auth.then(optionalHandler(wishlistApiHandler?.let { it::listWishlist })),
            "/api/wishlist" bind Method.POST to filters.auth.then(optionalHandler(wishlistApiHandler?.let { it::addToWishlist })),
            "/api/wishlist/{id}" bind Method.PUT to filters.auth.then(optionalHandler(wishlistApiHandler?.let { it::updateWishlistItem })),
            "/api/wishlist/{id}" bind Method.DELETE to filters.auth.then(optionalHandler(wishlistApiHandler?.let { it::deleteWishlistItem })),
            // Advanced search
            "/api/search/advanced" bind Method.POST to filters.auth.then(::advancedSearch),
        )

    // ─── Inline handler methods ──────────────────────────────────────────────

    private fun getOpdsCredentials(req: Request): Response {
        val svc = opdsCredentialsService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val userId = AuthenticatedUser.from(req)
        val info = svc.getCredentials(userId)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                org.runary.config.Json.mapper.writeValueAsString(
                    mapOf("configured" to (info != null), "opdsUsername" to info?.opdsUsername),
                ),
            )
    }

    private fun setOpdsCredentials(req: Request): Response {
        val svc = opdsCredentialsService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val userId = AuthenticatedUser.from(req)
        val body =
            runCatching {
                org.runary.config.Json.mapper
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
                org.runary.config.Json.mapper
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
                org.runary.config.Json.mapper.writeValueAsString(
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
                org.runary.config.Json.mapper
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
                org.runary.config.Json.mapper
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
                org.runary.config.Json.mapper
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
                org.runary.config.Json.mapper
                    .writeValueAsString(restrictions),
            )
    }

    private fun updateContentRestrictions(req: Request): Response {
        val svc = contentRestrictionsService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val userId = AuthenticatedUser.from(req)
        val body =
            runCatching {
                org.runary.config.Json.mapper
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
                        org.runary.config.Json.mapper
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
                org.runary.config.Json.mapper
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
                org.runary.config.Json.mapper.writeValueAsString(
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
                org.runary.config.Json.mapper
                    .writeValueAsString(mapOf("browseMode" to mode)),
            )
    }

    private fun setBrowseMode(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val body =
            runCatching {
                org.runary.config.Json.mapper
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
                org.runary.config.Json.mapper
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
                org.runary.config.Json.mapper
                    .writeValueAsString(svc.list(userId)),
            )
    }

    private fun createFilterPreset(req: Request): Response {
        val svc = filterPresetService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val userId = AuthenticatedUser.from(req)
        val request =
            runCatching {
                org.runary.config.Json.mapper
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
                org.runary.config.Json.mapper
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
                org.runary.config.Json.mapper
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
                org.runary.config.Json.mapper
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
                org.runary.config.Json.mapper
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
                org.runary.config.Json.mapper
                    .writeValueAsString(stats),
            )
    }

    /** GET /api/activity — returns the authenticated user's audit log entries */
    private fun listActivity(req: Request): Response {
        val svc = auditService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val userId = AuthenticatedUser.from(req)
        val limit = req.query("limit")?.toIntOrNull()?.coerceIn(1, 200) ?: 50
        val entries = svc.listForUser(userId, limit)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                org.runary.config.Json.mapper
                    .writeValueAsString(entries),
            )
    }

    private fun getLibraryStats(req: Request): Response {
        val svc = libraryStatsService ?: return Response(Status.SERVICE_UNAVAILABLE)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                org.runary.config.Json.mapper
                    .writeValueAsString(svc.getStats(AuthenticatedUser.from(req))),
            )
    }

    private fun getReadingTimeline(req: Request): Response {
        val svc = readingTimelineService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val userId = AuthenticatedUser.from(req)
        val days = req.query("days")?.toIntOrNull()?.coerceIn(7, 365) ?: 90
        val limit = req.query("limit")?.toIntOrNull()?.coerceIn(1, 200) ?: 50
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                org.runary.config.Json.mapper
                    .writeValueAsString(svc.getTimeline(userId, days, limit)),
            )
    }

    private fun getReadingGoal(req: Request): Response {
        val svc = readingGoalService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val year =
            req.query("year")?.toIntOrNull() ?: java.time.LocalDate
                .now()
                .year
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                org.runary.config.Json.mapper
                    .writeValueAsString(svc.getProgress(AuthenticatedUser.from(req), year)),
            )
    }

    private fun setReadingGoal(req: Request): Response {
        val svc = readingGoalService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val userId = AuthenticatedUser.from(req)
        val body =
            runCatching {
                org.runary.config.Json.mapper
                    .readTree(req.bodyString())
            }.getOrNull() ?: return Response(Status.BAD_REQUEST)
        val year =
            body.get("year")?.asInt() ?: java.time.LocalDate
                .now()
                .year
        val goal = body.get("goal")?.asInt() ?: return Response(Status.BAD_REQUEST)
        svc.setGoal(userId, year, goal)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                org.runary.config.Json.mapper
                    .writeValueAsString(svc.getProgress(userId, year)),
            )
    }

    private fun exportAnnotations(req: Request): Response {
        val svc = annotationExportService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val userId = AuthenticatedUser.from(req)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                org.runary.config.Json.mapper
                    .writeValueAsString(mapOf("annotations" to svc.getAnnotations(userId), "bookmarks" to svc.getBookmarks(userId))),
            )
    }

    private fun exportAnnotationsMarkdown(req: Request): Response {
        val svc = annotationExportService ?: return Response(Status.SERVICE_UNAVAILABLE)
        return Response(Status.OK)
            .header("Content-Type", "text/markdown; charset=utf-8")
            .header("Content-Disposition", "attachment; filename=\"runary-highlights.md\"")
            .body(svc.toMarkdown(AuthenticatedUser.from(req)))
    }

    private fun exportAnnotationsReadwise(req: Request): Response {
        val svc = annotationExportService ?: return Response(Status.SERVICE_UNAVAILABLE)
        return Response(Status.OK)
            .header("Content-Type", "text/csv; charset=utf-8")
            .header("Content-Disposition", "attachment; filename=\"runary-readwise.csv\"")
            .body(svc.toReadwiseCsv(AuthenticatedUser.from(req)))
    }

    private fun discover(req: Request): Response {
        val svc = discoveryService ?: return Response(Status.SERVICE_UNAVAILABLE)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                org.runary.config.Json.mapper
                    .writeValueAsString(svc.discover(AuthenticatedUser.from(req))),
            )
    }

    private fun pullPosition(req: Request): Response {
        val svc = positionSyncService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val userId = AuthenticatedUser.from(req)
        val bookId =
            req.uri.path
                .split("/")
                .let { p ->
                    val i = p.indexOf("books")
                    if (i >= 0 && i + 1 < p.size) p[i + 1] else null
                }?.let { runCatching { java.util.UUID.fromString(it) }.getOrNull() } ?: return Response(Status.BAD_REQUEST)
        val position = svc.pull(userId, bookId) ?: return Response(Status.NOT_FOUND)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                org.runary.config.Json.mapper
                    .writeValueAsString(position),
            )
    }

    private fun pushPosition(req: Request): Response {
        val svc = positionSyncService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val userId = AuthenticatedUser.from(req)
        val bookId =
            req.uri.path
                .split("/")
                .let { p ->
                    val i = p.indexOf("books")
                    if (i >= 0 && i + 1 < p.size) p[i + 1] else null
                }?.let { runCatching { java.util.UUID.fromString(it) }.getOrNull() } ?: return Response(Status.BAD_REQUEST)
        val body =
            runCatching {
                org.runary.config.Json.mapper
                    .readValue(req.bodyString(), org.runary.services.UpdatePositionRequest::class.java)
            }.getOrNull() ?: return Response(Status.BAD_REQUEST)
        svc.push(userId, bookId, body)
        return Response(Status.NO_CONTENT)
    }

    // ─── Public profile ───────────────────────────────────────────────────

    private fun getProfilePublicStatus(req: Request): Response {
        val svc = publicProfileService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val userId = AuthenticatedUser.from(req)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body("""{"public":${svc.isPublic(userId)}}""")
    }

    private fun setProfilePublic(req: Request): Response {
        val svc = publicProfileService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val userId = AuthenticatedUser.from(req)
        val body =
            runCatching {
                org.runary.config.Json.mapper
                    .readTree(req.bodyString())
            }.getOrNull()
                ?: return Response(Status.BAD_REQUEST)
        val enabled = body.get("public")?.asBoolean() ?: return Response(Status.BAD_REQUEST)
        svc.setPublic(userId, enabled)
        return Response(Status.NO_CONTENT)
    }

    /** Public endpoint — no auth required. Returns 404 if user not found or profile not public. */
    private fun getPublicProfile(req: Request): Response {
        val svc = publicProfileService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val username =
            req.uri.path
                .split("/")
                .lastOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: return Response(Status.BAD_REQUEST)
        val profile = svc.getProfile(username) ?: return Response(Status.NOT_FOUND)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                org.runary.config.Json.mapper
                    .writeValueAsString(profile),
            )
    }

    // ─── Reading speed ────────────────────────────────────────────────────

    private fun getReadingSpeed(req: Request): Response {
        val svc = readingSpeedService ?: return Response(Status.SERVICE_UNAVAILABLE)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                org.runary.config.Json.mapper
                    .writeValueAsString(svc.getStats(AuthenticatedUser.from(req))),
            )
    }

    // ─── Reading streaks widget ───────────────────────────────────────────

    private fun getStreaks(req: Request): Response {
        val svc = readingStatsService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val stats = svc.getStats(AuthenticatedUser.from(req), 365)
        val data = mapOf("currentStreak" to stats.currentStreak, "longestStreak" to stats.longestStreak, "averagePagesPerDay" to stats.averagePagesPerDay)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                org.runary.config.Json.mapper
                    .writeValueAsString(data),
            )
    }

    // ─── Book condition tracker ───────────────────────────────────────────

    private fun getBookCondition(req: Request): Response {
        val svc = bookConditionService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val userId = AuthenticatedUser.from(req)
        val bookId =
            req.uri.path
                .split("/")
                .let { p ->
                    val i = p.indexOf("books")
                    if (i >= 0 && i + 1 < p.size) p[i + 1] else null
                }?.let { runCatching { java.util.UUID.fromString(it) }.getOrNull() } ?: return Response(Status.BAD_REQUEST)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                org.runary.config.Json.mapper
                    .writeValueAsString(svc.get(userId, bookId)),
            )
    }

    private fun updateBookCondition(req: Request): Response {
        val svc = bookConditionService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val userId = AuthenticatedUser.from(req)
        val bookId =
            req.uri.path
                .split("/")
                .let { p ->
                    val i = p.indexOf("books")
                    if (i >= 0 && i + 1 < p.size) p[i + 1] else null
                }?.let { runCatching { java.util.UUID.fromString(it) }.getOrNull() } ?: return Response(Status.BAD_REQUEST)
        val body =
            runCatching {
                org.runary.config.Json.mapper
                    .readValue(req.bodyString(), org.runary.services.UpdateBookConditionRequest::class.java)
            }.getOrNull() ?: return Response(Status.BAD_REQUEST)
        svc.update(userId, bookId, body)
        return Response(Status.NO_CONTENT)
    }

    // ─── Shared annotations ──────────────────────────────────────────────

    private fun shareAnnotation(req: Request): Response {
        val svc = annotationService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val userId = AuthenticatedUser.from(req)
        val annotationId =
            req.uri.path
                .split("/")
                .let { p ->
                    val i = p.indexOf("annotations")
                    if (i >= 0 && i + 1 < p.size) p[i + 1] else null
                }?.let { runCatching { java.util.UUID.fromString(it) }.getOrNull() } ?: return Response(Status.BAD_REQUEST)
        val body =
            runCatching {
                org.runary.config.Json.mapper
                    .readTree(req.bodyString())
            }.getOrNull() ?: return Response(Status.BAD_REQUEST)
        val shared = body.get("shared")?.asBoolean() ?: return Response(Status.BAD_REQUEST)
        val note = body.get("note")?.asText()
        return if (svc.setShared(userId, annotationId, shared, note)) Response(Status.NO_CONTENT) else Response(Status.NOT_FOUND)
    }

    private fun getSharedAnnotations(req: Request): Response {
        val svc = annotationService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val bookId =
            req.uri.path
                .split("/")
                .let { p ->
                    val i = p.indexOf("books")
                    if (i >= 0 && i + 1 < p.size) p[i + 1] else null
                }?.let { runCatching { java.util.UUID.fromString(it) }.getOrNull() } ?: return Response(Status.BAD_REQUEST)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                org.runary.config.Json.mapper
                    .writeValueAsString(svc.getSharedAnnotations(bookId)),
            )
    }

    // ─── Advanced search ──────────────────────────────────────────────────

    private fun advancedSearch(req: Request): Response {
        val svc = advancedSearchService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val userId = AuthenticatedUser.from(req)
        val body =
            runCatching {
                org.runary.config.Json.mapper
                    .readValue(req.bodyString(), org.runary.services.AdvancedSearchRequest::class.java)
            }.getOrNull() ?: return Response(Status.BAD_REQUEST)
        val result = svc.search(userId, body)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                org.runary.config.Json.mapper
                    .writeValueAsString(result),
            )
    }
}
