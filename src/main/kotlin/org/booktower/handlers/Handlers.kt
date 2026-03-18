@file:Suppress("MatchingDeclarationName") // file is the router/wiring hub and contains AppHandler

package org.booktower.handlers

import org.booktower.config.StorageConfig
import org.booktower.config.TemplateRenderer
import org.booktower.filters.AuthenticatedUser
import org.booktower.filters.RateLimitFilter
import org.booktower.filters.adminFilter
import org.booktower.filters.jwtAuthFilter
import org.booktower.model.ThemeCatalog
import org.booktower.services.AdminService
import org.booktower.services.AlternativeCoverService
import org.booktower.services.AnalyticsService
import org.booktower.services.AnnotationService
import org.booktower.services.ApiTokenService
import org.booktower.services.AudiobookMetaService
import org.booktower.services.AuditService
import org.booktower.services.AuthService
import org.booktower.services.AuthorMetadataService
import org.booktower.services.BackgroundTaskService
import org.booktower.services.BookDeliveryService
import org.booktower.services.BookDropService
import org.booktower.services.BookFilesService
import org.booktower.services.BookNotebookService
import org.booktower.services.BookReviewService
import org.booktower.services.BookService
import org.booktower.services.BookmarkService
import org.booktower.services.BulkCoverService
import org.booktower.services.CalibreConversionService
import org.booktower.services.ComicMetadataService
import org.booktower.services.ComicService
import org.booktower.services.ContentRestrictionsService
import org.booktower.services.CreateEmailProviderRequest
import org.booktower.services.CreateNotebookRequest
import org.booktower.services.CreateReviewRequest
import org.booktower.services.CreateScheduledTaskRequest
import org.booktower.services.DuplicateDetectionService
import org.booktower.services.EmailProviderService
import org.booktower.services.EmailService
import org.booktower.services.EpubMetadataService
import org.booktower.services.ExportService
import org.booktower.services.FilterPresetService
import org.booktower.services.FontService
import org.booktower.services.GoodreadsImportService
import org.booktower.services.HardcoverSyncService
import org.booktower.services.JournalService
import org.booktower.services.JwtService
import org.booktower.services.KOReaderSyncService
import org.booktower.services.KoboSyncService
import org.booktower.services.KomgaApiService
import org.booktower.services.LibraryAccessService
import org.booktower.services.LibraryHealthService
import org.booktower.services.LibraryService
import org.booktower.services.LibraryWatchService
import org.booktower.services.ListeningSessionService
import org.booktower.services.ListeningStatsService
import org.booktower.services.MagicShelfService
import org.booktower.services.MetadataFetchService
import org.booktower.services.MetadataLockService
import org.booktower.services.MetadataProposalService
import org.booktower.services.NotificationService
import org.booktower.services.OidcService
import org.booktower.services.OpdsCredentialsService
import org.booktower.services.PasswordResetService
import org.booktower.services.PdfMetadataService
import org.booktower.services.ReaderPreferencesService
import org.booktower.services.ReadingSessionService
import org.booktower.services.ReadingStatsService
import org.booktower.services.RecommendationService
import org.booktower.services.SaveFilterPresetRequest
import org.booktower.services.ScheduledTaskService
import org.booktower.services.TelemetryService
import org.booktower.services.UpdateAudiobookMetaRequest
import org.booktower.services.UpdateEmailProviderRequest
import org.booktower.services.UpdateNotebookRequest
import org.booktower.services.UpdateReviewRequest
import org.booktower.services.UpdateScheduledTaskRequest
import org.booktower.services.UserPermissionsService
import org.booktower.services.UserSettingsService
import org.booktower.web.WebContext
import org.booktower.weblate.WeblateHandler
import org.http4k.core.*
import org.http4k.core.body.form
import org.http4k.core.cookie.Cookie
import org.http4k.core.cookie.cookie
import org.http4k.routing.ResourceLoader
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.routing.static
import org.slf4j.LoggerFactory

/** All book/audio formats supported by BookTower, with MIME types. */
val SUPPORTED_FORMATS: Map<String, String> =
    mapOf(
        "pdf" to "application/pdf",
        "epub" to "application/epub+zip",
        "mobi" to "application/x-mobipocket-ebook",
        "azw3" to "application/x-mobi8-ebook",
        "fb2" to "application/xml",
        "cbz" to "application/zip",
        "cbr" to "application/x-rar-compressed",
        "djvu" to "image/vnd.djvu",
        "mp3" to "audio/mpeg",
        "m4b" to "audio/mp4",
        "m4a" to "audio/mp4",
        "ogg" to "audio/ogg",
        "flac" to "audio/flac",
        "aac" to "audio/aac",
    )

private val logger = LoggerFactory.getLogger("booktower.AppHandler")

class AppHandler(
    private val authService: AuthService,
    private val libraryService: LibraryService,
    private val bookService: BookService,
    private val bookmarkService: BookmarkService,
    private val userSettingsService: UserSettingsService,
    private val pdfMetadataService: PdfMetadataService,
    private val epubMetadataService: EpubMetadataService,
    private val adminService: AdminService,
    private val jwtService: JwtService,
    private val storageConfig: StorageConfig,
    private val templateRenderer: TemplateRenderer,
    private val weblateHandler: WeblateHandler,
    private val analyticsService: AnalyticsService,
    private val annotationService: AnnotationService,
    private val metadataFetchService: MetadataFetchService,
    private val magicShelfService: MagicShelfService,
    private val passwordResetService: PasswordResetService,
    private val emailService: EmailService,
    private val appBaseUrl: String,
    private val registrationOpen: Boolean,
    private val apiTokenService: ApiTokenService,
    private val exportService: ExportService,
    private val comicService: ComicService,
    private val goodreadsImportService: GoodreadsImportService,
    private val readingSessionService: ReadingSessionService,
    private val seedService: org.booktower.services.SeedService,
    private val libraryWatchService: LibraryWatchService? = null,
    private val duplicateDetectionService: DuplicateDetectionService? = null,
    private val auditService: AuditService? = null,
    private val backgroundTaskService: BackgroundTaskService? = null,
    private val journalService: JournalService? = null,
    private val libraryHealthService: LibraryHealthService? = null,
    private val authorMetadataService: AuthorMetadataService? = null,
    private val recommendationService: RecommendationService? = null,
    private val alternativeCoverService: AlternativeCoverService? = null,
    private val bookDeliveryService: BookDeliveryService? = null,
    private val bookDropService: BookDropService? = null,
    private val oidcService: OidcService? = null,
    private val koboSyncService: KoboSyncService? = null,
    private val koReaderSyncService: KOReaderSyncService? = null,
    private val komgaApiService: KomgaApiService? = null,
    private val fontService: FontService? = null,
    private val readerPreferencesService: ReaderPreferencesService? = null,
    private val userPermissionsService: UserPermissionsService? = null,
    private val libraryAccessService: LibraryAccessService? = null,
    private val metadataLockService: MetadataLockService? = null,
    private val readingStatsService: ReadingStatsService? = null,
    private val listeningSessionService: ListeningSessionService? = null,
    private val listeningStatsService: ListeningStatsService? = null,
    private val metadataProposalService: MetadataProposalService? = null,
    private val hardcoverSyncService: HardcoverSyncService? = null,
    private val opdsCredentialsService: OpdsCredentialsService? = null,
    private val bookFilesService: BookFilesService? = null,
    private val emailProviderService: EmailProviderService? = null,
    private val scheduledTaskService: ScheduledTaskService? = null,
    private val bulkCoverService: BulkCoverService? = null,
    private val comicMetadataService: ComicMetadataService? = null,
    private val contentRestrictionsService: ContentRestrictionsService? = null,
    private val bookReviewService: BookReviewService? = null,
    private val bookNotebookService: BookNotebookService? = null,
    private val notificationService: NotificationService? = null,
    private val audiobookMetaService: AudiobookMetaService? = null,
    private val filterPresetService: FilterPresetService? = null,
    private val telemetryService: TelemetryService? = null,
    private val communityRatingService: org.booktower.services.CommunityRatingService? = null,
    private val comicPageHashService: org.booktower.services.ComicPageHashService? = null,
    private val demoMode: Boolean = false,
) {
    private val authHandler =
        AuthHandler2(
            authService,
            userSettingsService,
            passwordResetService,
            emailService,
            appBaseUrl,
            registrationOpen,
            auditService,
            oidcService?.config?.forceOnlyMode ?: false,
        )
    private val libraryHandler = LibraryHandler2(libraryService, backgroundTaskService, storageConfig)
    private val bookHandler = BookHandler2(bookService, readingSessionService)
    private val bookmarkHandler = BookmarkHandler(bookmarkService)
    private val calibreService = CalibreConversionService(java.io.File(storageConfig.tempPath, "calibre-cache"))
    private val fileHandler =
        FileHandler(bookService, pdfMetadataService, epubMetadataService, storageConfig, calibreService = calibreService)
    private val settingsHandler = UserSettingsHandler(userSettingsService)
    private val adminHandler =
        AdminHandler(adminService, templateRenderer, passwordResetService, seedService, emailService, appBaseUrl, duplicateDetectionService, auditService, userPermissionsService, libraryAccessService, comicPageHashService)
    private val pageHandler =
        PageHandler(jwtService, authService, libraryService, bookService, bookmarkService, userSettingsService, analyticsService, annotationService, metadataFetchService, magicShelfService, templateRenderer, readingSessionService, libraryWatchService)
    private val backgroundTaskHandler = backgroundTaskService?.let { BackgroundTaskHandler(it) }
    private val journalHandler = journalService?.let { JournalHandler(it) }
    private val oidcHandler = oidcService?.let { OidcHandler(it, authService) }
    private val koboSyncHandler = koboSyncService?.let { KoboSyncHandler(it) }
    private val koReaderSyncHandler = koReaderSyncService?.let { KOReaderSyncHandler(it) }
    private val komgaApiHandler = komgaApiService?.let { KomgaApiHandler(it) }
    private val fontHandler = fontService?.let { FontHandler(it) }
    private val readerPreferencesHandler = readerPreferencesService?.let { ReaderPreferencesHandler(it) }
    private val opdsHandler = OpdsHandler(authService, libraryService, bookService, storageConfig, apiTokenService, opdsCredentialsService)
    private val apiTokenHandler = ApiTokenHandler(apiTokenService, jwtService)
    private val exportHandler = ExportHandler(exportService, jwtService)
    private val goodreadsImportHandler = GoodreadsImportHandler(goodreadsImportService, jwtService)
    private val bulkBookHandler = BulkBookHandler(bookService)
    private val authFilter = jwtAuthFilter(jwtService) { userId: java.util.UUID -> authService.getUserById(userId) != null }
    private val adminFilter = authFilter.then(adminFilter())
    private val authRateLimit = RateLimitFilter(maxRequests = 10, windowSeconds = 60)

    fun routes(): RoutingHttpHandler =
        routes(
            "/static" bind static(ResourceLoader.Classpath("/static")),
            "/covers/{filename}" bind Method.GET to fileHandler::cover,
            "/manifest.json" bind Method.GET to ::pwaManifest,
            // HTML pages
            "/" bind Method.GET to ::index,
            "/login" bind Method.GET to ::loginPage,
            "/register" bind Method.GET to ::registerPage,
            "/forgot-password" bind Method.GET to ::forgotPasswordPage,
            "/reset-password" bind Method.GET to ::resetPasswordPage,
            "/libraries" bind Method.GET to pageHandler::libraries,
            "/libraries/{id}" bind Method.GET to pageHandler::library,
            "/books/{id}" bind Method.GET to pageHandler::book,
            "/books/{id}/read" bind Method.GET to pageHandler::reader,
            "/search" bind Method.GET to pageHandler::search,
            "/queue" bind Method.GET to authFilter.then(pageHandler::queue),
            "/series" bind Method.GET to authFilter.then(pageHandler::seriesList),
            "/series/{name}" bind Method.GET to authFilter.then(pageHandler::series),
            "/authors" bind Method.GET to authFilter.then(pageHandler::authorList),
            "/authors/{name}" bind Method.GET to authFilter.then(pageHandler::author),
            "/tags" bind Method.GET to authFilter.then(pageHandler::tagList),
            "/tags/{name}" bind Method.GET to authFilter.then(pageHandler::tag),
            "/profile" bind Method.GET to pageHandler::profile,
            "/analytics" bind Method.GET to pageHandler::analytics,
            "/ui/preferences/analytics" bind Method.POST to pageHandler::setAnalytics,
            "/admin" bind Method.GET to adminFilter.then(adminHandler::adminPage),
            // OIDC / SSO
            "/auth/oidc/login" bind Method.GET to (
                oidcHandler?.let { it::login } ?: { _ ->
                    Response(Status.NOT_FOUND).body("""{"error":"OIDC not enabled"}""")
                }
            ),
            "/auth/oidc/callback" bind Method.GET to (
                oidcHandler?.let { it::callback } ?: { _ ->
                    Response(Status.NOT_FOUND).body("""{"error":"OIDC not enabled"}""")
                }
            ),
            "/auth/oidc/backchannel-logout" bind Method.POST to (
                oidcHandler?.let { it::backchannelLogout } ?: { _ ->
                    Response(Status.NOT_FOUND).body("""{"error":"OIDC not enabled"}""")
                }
            ),
            "/api/oidc/status" bind Method.GET to (
                oidcHandler?.let { it::status } ?: { _ ->
                    Response(
                        Status.OK,
                    ).header(
                        "Content-Type",
                        "application/json",
                    ).body("""{"enabled":false,"forceOnly":false,"groupMappingEnabled":false,"discoveryAvailable":false,"loginUrl":null}""")
                }
            ),
            // Auth (rate-limited: 10 requests per 60 s per IP)
            "/auth/register" bind Method.POST to authRateLimit.then(authHandler::register),
            "/auth/login" bind Method.POST to authRateLimit.then(authHandler::login),
            "/auth/logout" bind Method.POST to authHandler::logout,
            "/auth/forgot-password" bind Method.POST to authRateLimit.then(authHandler::forgotPassword),
            "/auth/reset-password" bind Method.POST to authRateLimit.then(authHandler::resetPassword),
            "/auth/refresh" bind Method.POST to authRateLimit.then(authHandler::refresh),
            "/auth/revoke" bind Method.POST to authHandler::revokeToken,
            // HTMX UI mutations
            "/ui/libraries" bind Method.POST to pageHandler::createLibrary,
            "/ui/libraries/{id}" bind Method.DELETE to pageHandler::deleteLibrary,
            "/ui/libraries/{id}/rename" bind Method.POST to pageHandler::renameLibrary,
            "/ui/libraries/{libId}/books" bind Method.POST to pageHandler::createBook,
            "/ui/books/{id}" bind Method.DELETE to pageHandler::deleteBook,
            "/ui/books/{id}/move" bind Method.POST to pageHandler::moveBook,
            "/ui/books/{id}/meta" bind Method.POST to pageHandler::editBook,
            "/ui/books/{id}/progress" bind Method.POST to pageHandler::updateProgress,
            "/ui/books/{id}/status" bind Method.POST to pageHandler::setStatus,
            "/ui/books/{id}/rating" bind Method.POST to pageHandler::setRating,
            "/ui/books/{id}/tags" bind Method.POST to pageHandler::setTags,
            "/ui/books/{id}/bookmarks" bind Method.POST to pageHandler::createBookmark,
            "/ui/bookmarks/{id}" bind Method.DELETE to pageHandler::deleteBookmark,
            "/ui/goal" bind Method.POST to pageHandler::setGoal,
            "/ui/books/{id}/fetch-metadata" bind Method.POST to pageHandler::fetchMetadata,
            "/ui/books/{id}/annotations" bind Method.GET to pageHandler::getAnnotations,
            "/ui/books/{id}/annotations" bind Method.POST to pageHandler::createAnnotation,
            "/ui/annotations/{id}" bind Method.DELETE to pageHandler::deleteAnnotation,
            // Journal / notebook
            "/api/books/{id}/journal" bind Method.GET to
                authFilter.then(
                    journalHandler?.let { it::list } ?: { _ ->
                        Response(Status.SERVICE_UNAVAILABLE)
                    },
                ),
            "/api/books/{id}/journal" bind Method.POST to
                authFilter.then(
                    journalHandler?.let { it::create } ?: { _ ->
                        Response(Status.SERVICE_UNAVAILABLE)
                    },
                ),
            "/api/books/{id}/journal/{entryId}" bind Method.PUT to
                authFilter.then(
                    journalHandler?.let { it::update } ?: { _ ->
                        Response(Status.SERVICE_UNAVAILABLE)
                    },
                ),
            "/api/books/{id}/journal/{entryId}" bind Method.DELETE to
                authFilter.then(
                    journalHandler?.let { it::delete } ?: { _ ->
                        Response(Status.SERVICE_UNAVAILABLE)
                    },
                ),
            "/api/journal" bind Method.GET to
                authFilter.then(
                    journalHandler?.let { it::listAll } ?: { _ ->
                        Response(Status.SERVICE_UNAVAILABLE)
                    },
                ),
            // Health & version (public, no auth required)
            "/health" bind Method.GET to { Response(Status.OK).header("Content-Type", "application/json").body("""{"status":"ok"}""") },
            "/api/version" bind Method.GET to {
                Response(Status.OK)
                    .header("Content-Type", "application/json")
                    .body(
                        org.booktower.config.Json.mapper
                            .writeValueAsString(org.booktower.services.VersionService.info),
                    )
            },
            // Preferences
            "/preferences/theme" bind Method.POST to ::setTheme,
            "/preferences/lang" bind Method.POST to ::setLanguage,
            // JSON API
            "/api/libraries" bind Method.GET to authFilter.then(libraryHandler::list),
            "/api/libraries" bind Method.POST to authFilter.then(libraryHandler::create),
            "/api/libraries/{id}" bind Method.DELETE to authFilter.then(libraryHandler::delete),
            "/api/libraries/{id}/settings" bind Method.GET to authFilter.then(libraryHandler::getSettings),
            "/api/libraries/{id}/settings" bind Method.PUT to authFilter.then(libraryHandler::updateSettings),
            "/api/libraries/{id}/organize" bind Method.POST to authFilter.then(libraryHandler::organize),
            "/api/libraries/{id}/scan/async" bind Method.POST to authFilter.then(libraryHandler::scanAsync),
            "/api/libraries/{id}/scan/{jobId}" bind Method.GET to authFilter.then(libraryHandler::scanStatus),
            "/api/libraries/{id}/scan" bind Method.POST to authFilter.then(libraryHandler::scan),
            "/api/libraries/{id}/icon" bind Method.POST to authFilter.then(libraryHandler::uploadIcon),
            "/api/libraries/{id}/icon" bind Method.GET to authFilter.then(libraryHandler::getIcon),
            "/api/libraries/{id}/icon" bind Method.DELETE to authFilter.then(libraryHandler::deleteIcon),
            "/api/books" bind Method.GET to authFilter.then(bookHandler::list),
            "/api/books" bind Method.POST to authFilter.then(bookHandler::create),
            // Bulk routes must come before /api/books/{id}/... to avoid {id} matching "bulk"
            "/api/books/bulk/move" bind Method.POST to authFilter.then(bulkBookHandler::move),
            "/api/books/bulk/delete" bind Method.POST to authFilter.then(bulkBookHandler::delete),
            "/api/books/bulk/tag" bind Method.POST to authFilter.then(bulkBookHandler::tag),
            "/api/books/bulk/status" bind Method.POST to authFilter.then(bulkBookHandler::status),
            "/api/books/{id}" bind Method.GET to authFilter.then(bookHandler::get),
            "/api/books/{id}" bind Method.PUT to authFilter.then(bookHandler::update),
            "/api/books/{id}" bind Method.DELETE to authFilter.then(bookHandler::delete),
            "/api/books/{id}/progress" bind Method.PUT to authFilter.then(bookHandler::updateProgress),
            "/api/books/{id}/status" bind Method.POST to authFilter.then(::setBookStatus),
            "/api/books/{id}/merge" bind Method.POST to authFilter.then(::mergeBook),
            "/api/books/{id}/community-rating" bind Method.GET to authFilter.then(::getCommunityRating),
            "/api/books/{id}/community-rating/fetch" bind Method.POST to authFilter.then(::fetchCommunityRating),
            "/api/books/{id}/sessions" bind Method.GET to authFilter.then(bookHandler::sessions),
            "/api/recent" bind Method.GET to authFilter.then(bookHandler::recent),
            "/api/search" bind Method.GET to authFilter.then(bookHandler::search),
            "/api/books/{id}/apply-filename-metadata" bind Method.POST to authFilter.then(::applyFilenameMetadata),
            "/api/books/{id}/apply-sidecar-metadata" bind Method.POST to authFilter.then(::applySidecarMetadata),
            "/api/formats" bind Method.GET to {
                Response(Status.OK)
                    .header("Content-Type", "application/json")
                    .body(
                        org.booktower.config.Json.mapper
                            .writeValueAsString(mapOf("formats" to org.booktower.handlers.SUPPORTED_FORMATS)),
                    )
            },
            "/api/metadata/search" bind Method.GET to authFilter.then(::metadataSearch),
            "/api/metadata/sources" bind Method.GET to authFilter.then(::metadataSources),
            // OPDS separate credentials
            "/api/user/opds-credentials" bind Method.GET to authFilter.then(::getOpdsCredentials),
            "/api/user/opds-credentials" bind Method.PUT to authFilter.then(::setOpdsCredentials),
            "/api/user/opds-credentials" bind Method.DELETE to authFilter.then(::clearOpdsCredentials),
            // Hardcover.app sync
            "/api/user/hardcover/key" bind Method.PUT to authFilter.then(::setHardcoverKey),
            "/api/user/hardcover/status" bind Method.GET to authFilter.then(::hardcoverStatus),
            "/api/books/{id}/hardcover/sync" bind Method.POST to authFilter.then(::syncBookToHardcover),
            "/api/books/{id}/hardcover/mapping" bind Method.GET to authFilter.then(::getHardcoverMapping),
            // Metadata proposal/review workflow
            "/api/books/{id}/metadata/propose" bind Method.POST to authFilter.then(::proposeMetadata),
            "/api/books/{id}/metadata/proposals" bind Method.GET to authFilter.then(::listMetadataProposals),
            "/api/books/{id}/metadata/proposals/{proposalId}/apply" bind Method.POST to authFilter.then(::applyMetadataProposal),
            "/api/books/{id}/metadata/proposals/{proposalId}" bind Method.DELETE to authFilter.then(::dismissMetadataProposal),
            "/api/authors/{name}/metadata" bind Method.GET to authFilter.then(::authorMetadata),
            "/api/books/{id}/similar" bind Method.GET to authFilter.then(::similarBooks),
            "/api/books/{id}/covers/alternatives" bind Method.GET to authFilter.then(::alternativeCovers),
            "/api/books/{id}/cover/apply-url" bind Method.POST to authFilter.then(::applyCoverUrl),
            "/api/books/{id}/send" bind Method.POST to authFilter.then(::sendBook),
            "/api/delivery/recipients" bind Method.GET to authFilter.then(::listRecipients),
            "/api/delivery/recipients" bind Method.POST to authFilter.then(::addRecipient),
            "/api/delivery/recipients/{id}" bind Method.DELETE to authFilter.then(::deleteRecipient),
            "/api/bookdrop" bind Method.GET to authFilter.then(::bookDropList),
            "/api/bookdrop/{filename}/import" bind Method.POST to authFilter.then(::bookDropImport),
            "/api/bookdrop/{filename}" bind Method.DELETE to authFilter.then(::bookDropDiscard),
            "/api/libraries/health" bind Method.GET to authFilter.then(::libraryHealth),
            "/api/bookmarks" bind Method.GET to authFilter.then(bookmarkHandler::list),
            "/api/bookmarks" bind Method.POST to authFilter.then(bookmarkHandler::create),
            "/api/bookmarks/{id}" bind Method.DELETE to authFilter.then(bookmarkHandler::delete),
            "/api/books/{id}/upload" bind Method.POST to authFilter.then(fileHandler::upload),
            "/api/books/{id}/cover" bind Method.POST to authFilter.then(fileHandler::uploadCover),
            "/api/books/{id}/file" bind Method.GET to authFilter.then(fileHandler::download),
            "/api/books/{id}/kepub" bind Method.GET to authFilter.then(fileHandler::downloadKepub),
            "/api/books/{id}/read-content" bind Method.GET to authFilter.then(fileHandler::readContent),
            "/api/books/{id}/audio" bind Method.GET to authFilter.then(fileHandler::audioStream),
            "/api/books/{id}/chapters" bind Method.GET to authFilter.then(fileHandler::listChapters),
            "/api/books/{id}/chapters" bind Method.POST to authFilter.then(fileHandler::uploadChapter),
            "/api/books/{id}/chapters/{trackIndex}" bind Method.GET to authFilter.then(fileHandler::audioStreamChapter),
            "/api/books/{id}/chapters/{trackIndex}" bind Method.DELETE to authFilter.then(fileHandler::deleteChapter),
            "/api/books/{id}/chapters/{trackIndex}" bind Method.PUT to authFilter.then(fileHandler::updateChapterMeta),
            "/api/books/{id}/formats" bind Method.GET to authFilter.then(::listBookFiles),
            "/api/books/{id}/formats" bind Method.POST to authFilter.then(::addBookFile),
            "/api/books/{id}/formats/{fileId}" bind Method.DELETE to authFilter.then(::removeBookFile),
            "/api/books/{id}/comic/pages" bind Method.GET to authFilter.then(::comicPages),
            "/api/books/{id}/comic/{page}" bind Method.GET to authFilter.then(::comicPage),
            "/api/auth/change-password" bind Method.POST to authRateLimit.then(authFilter.then(authHandler::changePassword)),
            "/api/auth/change-email" bind Method.POST to authRateLimit.then(authFilter.then(authHandler::changeEmail)),
            "/api/settings" bind Method.GET to authFilter.then(settingsHandler::getAll),
            "/api/settings/{key}" bind Method.PUT to authFilter.then(settingsHandler::set),
            "/api/settings/{key}" bind Method.DELETE to authFilter.then(settingsHandler::delete),
            // Admin API
            "/admin/seed" bind Method.POST to adminFilter.then(adminHandler::seed),
            "/admin/seed/files" bind Method.POST to adminFilter.then(adminHandler::seedFiles),
            "/admin/seed/librivox" bind Method.POST to adminFilter.then(adminHandler::seedLibrivox),
            "/api/admin/password-reset-tokens" bind Method.GET to adminFilter.then(adminHandler::listResetTokens),
            "/api/admin/users" bind Method.GET to adminFilter.then(adminHandler::listUsers),
            "/api/admin/users/{userId}/promote" bind Method.POST to adminFilter.then(adminHandler::promote),
            "/api/admin/users/{userId}/demote" bind Method.POST to adminFilter.then(adminHandler::demote),
            "/api/admin/users/{userId}/reset-password" bind Method.POST to adminFilter.then(adminHandler::generateResetLink),
            "/api/admin/users/{userId}/permissions" bind Method.GET to adminFilter.then(adminHandler::getPermissions),
            "/api/admin/users/{userId}/permissions" bind Method.PUT to adminFilter.then(adminHandler::setPermissions),
            "/api/admin/users/{userId}/library-access" bind Method.GET to adminFilter.then(adminHandler::getLibraryAccess),
            "/api/admin/users/{userId}/library-access" bind Method.PUT to adminFilter.then(adminHandler::setLibraryRestricted),
            "/api/admin/users/{userId}/library-access" bind Method.POST to adminFilter.then(adminHandler::grantLibraryAccess),
            "/api/admin/users/{userId}/library-access/{libraryId}" bind Method.DELETE to
                adminFilter.then(
                    adminHandler::revokeLibraryAccess,
                ),
            "/api/admin/users/{userId}" bind Method.DELETE to adminFilter.then(adminHandler::deleteUser),
            "/api/admin/email-providers" bind Method.GET to adminFilter.then(::listEmailProviders),
            "/api/admin/email-providers" bind Method.POST to adminFilter.then(::createEmailProvider),
            "/api/admin/email-providers/{id}" bind Method.PUT to adminFilter.then(::updateEmailProvider),
            "/api/admin/email-providers/{id}" bind Method.DELETE to adminFilter.then(::deleteEmailProvider),
            "/api/admin/email-providers/{id}/set-default" bind Method.POST to adminFilter.then(::setDefaultEmailProvider),
            "/api/admin/scheduled-tasks" bind Method.GET to adminFilter.then(::listScheduledTasks),
            "/api/admin/scheduled-tasks" bind Method.POST to adminFilter.then(::createScheduledTask),
            "/api/admin/scheduled-tasks/{id}" bind Method.PUT to adminFilter.then(::updateScheduledTask),
            "/api/admin/scheduled-tasks/{id}" bind Method.DELETE to adminFilter.then(::deleteScheduledTask),
            "/api/admin/scheduled-tasks/{id}/trigger" bind Method.POST to adminFilter.then(::triggerScheduledTask),
            "/api/admin/scheduled-tasks/{id}/history" bind Method.GET to adminFilter.then(::scheduledTaskHistory),
            "/api/covers/regenerate" bind Method.POST to authFilter.then(::bulkRegenerateCovers),
            "/api/books/{id}/notebooks" bind Method.GET to authFilter.then(::listNotebooks),
            "/api/books/{id}/notebooks" bind Method.POST to authFilter.then(::createNotebook),
            "/api/books/{id}/notebooks/{notebookId}" bind Method.GET to authFilter.then(::getNotebook),
            "/api/books/{id}/notebooks/{notebookId}" bind Method.PUT to authFilter.then(::updateNotebook),
            "/api/books/{id}/notebooks/{notebookId}" bind Method.DELETE to authFilter.then(::deleteNotebook),
            "/api/books/{id}/reviews" bind Method.GET to authFilter.then(::listReviews),
            "/api/books/{id}/reviews" bind Method.POST to authFilter.then(::createReview),
            "/api/books/{id}/reviews/{reviewId}" bind Method.PUT to authFilter.then(::updateReview),
            "/api/books/{id}/reviews/{reviewId}" bind Method.DELETE to authFilter.then(::deleteReview),
            "/api/user/content-restrictions" bind Method.GET to authFilter.then(::getContentRestrictions),
            "/api/user/content-restrictions" bind Method.PUT to authFilter.then(::updateContentRestrictions),
            "/api/books/{id}/comic-metadata" bind Method.GET to authFilter.then(::getComicMetadata),
            "/api/books/{id}/comic-metadata" bind Method.PUT to authFilter.then(::updateComicMetadata),
            "/api/demo/status" bind Method.GET to ::demoStatus,
            "/api/telemetry/status" bind Method.GET to authFilter.then(::telemetryStatus),
            "/api/telemetry/opt-in" bind Method.POST to authFilter.then(::telemetryOptIn),
            "/api/telemetry/opt-out" bind Method.POST to authFilter.then(::telemetryOptOut),
            "/api/admin/telemetry/stats" bind Method.GET to adminFilter.then(::telemetryStats),
            "/api/setup/status" bind Method.GET to authFilter.then(::setupStatus),
            "/api/setup/complete" bind Method.POST to authFilter.then(::completeSetup),
            "/api/setup/steps/{step}/complete" bind Method.POST to authFilter.then(::completeSetupStep),
            "/api/user/preferences/browse-mode" bind Method.GET to authFilter.then(::getBrowseMode),
            "/api/user/preferences/browse-mode" bind Method.PUT to authFilter.then(::setBrowseMode),
            "/api/user/filter-presets" bind Method.GET to authFilter.then(::listFilterPresets),
            "/api/user/filter-presets" bind Method.POST to authFilter.then(::createFilterPreset),
            "/api/user/filter-presets/{id}" bind Method.GET to authFilter.then(::getFilterPreset),
            "/api/user/filter-presets/{id}" bind Method.PUT to authFilter.then(::updateFilterPreset),
            "/api/user/filter-presets/{id}" bind Method.DELETE to authFilter.then(::deleteFilterPreset),
            "/api/books/{id}/audiobook-meta" bind Method.GET to authFilter.then(::getAudiobookMeta),
            "/api/books/{id}/audiobook-meta" bind Method.PUT to authFilter.then(::updateAudiobookMeta),
            "/api/books/{id}/audiobook-meta" bind Method.DELETE to authFilter.then(::deleteAudiobookMeta),
            "/api/books/{id}/audiobook-cover" bind Method.POST to authFilter.then(::uploadAudiobookCover),
            "/api/books/{id}/audiobook-cover" bind Method.GET to authFilter.then(::getAudiobookCover),
            "/api/notifications" bind Method.GET to authFilter.then(::listNotifications),
            "/api/notifications/count" bind Method.GET to authFilter.then(::getNotificationCount),
            "/api/notifications/stream" bind Method.GET to authFilter.then(::streamNotifications),
            "/api/notifications/read-all" bind Method.POST to authFilter.then(::markAllNotificationsRead),
            "/api/notifications/{id}/read" bind Method.POST to authFilter.then(::markNotificationRead),
            "/api/notifications/{id}" bind Method.DELETE to authFilter.then(::deleteNotification),
            "/api/admin/duplicates" bind Method.GET to adminFilter.then(adminHandler::findDuplicates),
            "/api/admin/comic-page-duplicates" bind Method.GET to adminFilter.then(adminHandler::findComicPageDuplicates),
            "/api/admin/audit" bind Method.GET to adminFilter.then(adminHandler::listAuditLog),
            "/api/admin/tasks" bind Method.GET to
                adminFilter.then(
                    backgroundTaskHandler?.let { it::listAll } ?: { _ ->
                        Response(Status.SERVICE_UNAVAILABLE)
                    },
                ),
            // Weblate translation sync (admin-only endpoints, require Weblate to be enabled)
            "/api/weblate/pull" bind Method.POST to weblateHandler::pull,
            "/api/weblate/push" bind Method.POST to weblateHandler::push,
            "/api/weblate/status" bind Method.GET to weblateHandler::status,
            // API tokens
            "/api/tokens" bind Method.GET to authFilter.then(apiTokenHandler::list),
            "/api/tokens" bind Method.POST to authFilter.then(apiTokenHandler::create),
            "/api/tokens/{id}" bind Method.DELETE to authFilter.then(apiTokenHandler::revoke),
            // Export & Import
            "/api/export" bind Method.GET to authFilter.then(exportHandler::export),
            "/api/import/goodreads" bind Method.POST to authFilter.then(goodreadsImportHandler::import),
            "/api/tasks" bind Method.GET to
                authFilter.then(
                    backgroundTaskHandler?.let { it::list } ?: { _ ->
                        Response(Status.SERVICE_UNAVAILABLE)
                    },
                ),
            "/api/tasks/{id}" bind Method.DELETE to
                authFilter.then(
                    backgroundTaskHandler?.let { it::dismiss } ?: { _ ->
                        Response(Status.SERVICE_UNAVAILABLE)
                    },
                ),
            // Smart shelves
            "/shelves/{id}" bind Method.GET to pageHandler::magicShelf,
            "/ui/shelves" bind Method.POST to pageHandler::createMagicShelf,
            "/ui/shelves/{id}" bind Method.DELETE to pageHandler::deleteMagicShelf,
            "/api/shelves" bind Method.GET to authFilter.then(::listShelves),
            "/api/shelves" bind Method.POST to authFilter.then(::createShelf),
            "/api/shelves/{id}/share" bind Method.POST to authFilter.then(::shareShelf),
            "/api/shelves/{id}/share" bind Method.DELETE to authFilter.then(::unshareShelf),
            "/public/shelf/{token}" bind Method.GET to ::getPublicShelf,
            // OPDS Catalog 1.2 (HTTP Basic Auth — no JWT required)
            "/opds/catalog" bind Method.GET to opdsHandler::catalog,
            "/opds/catalog/{libraryId}" bind Method.GET to opdsHandler::library,
            "/opds/books/{id}/file" bind Method.GET to opdsHandler::download,
            "/opds/books/{id}/chapters/{trackIndex}" bind Method.GET to opdsHandler::streamChapter,
            // Kobo device sync
            "/api/kobo/devices" bind Method.POST to
                authFilter.then(
                    koboSyncHandler?.let { it::register } ?: { _ ->
                        Response(Status.SERVICE_UNAVAILABLE)
                    },
                ),
            "/api/kobo/devices" bind Method.GET to
                authFilter.then(
                    koboSyncHandler?.let { it::listDevices } ?: { _ ->
                        Response(Status.SERVICE_UNAVAILABLE)
                    },
                ),
            "/api/kobo/devices/{token}" bind Method.DELETE to
                authFilter.then(
                    koboSyncHandler?.let { it::deleteDevice } ?: { _ ->
                        Response(Status.SERVICE_UNAVAILABLE)
                    },
                ),
            "/kobo/{token}/v1/initialization" bind Method.GET to (
                koboSyncHandler?.let { it::initialization } ?: { _ ->
                    Response(Status.SERVICE_UNAVAILABLE)
                }
            ),
            "/kobo/{token}/v1/library/sync" bind Method.POST to (
                koboSyncHandler?.let { it::sync } ?: { _ ->
                    Response(Status.SERVICE_UNAVAILABLE)
                }
            ),
            "/kobo/{token}/v1/library/snapshot" bind Method.GET to (
                koboSyncHandler?.let { it::snapshot } ?: { _ ->
                    Response(Status.SERVICE_UNAVAILABLE)
                }
            ),
            "/kobo/{token}/v1/library/{bookId}/reading-state" bind Method.PUT to (
                koboSyncHandler?.let { it::readingState } ?: { _ ->
                    Response(Status.SERVICE_UNAVAILABLE)
                }
            ),
            // KOReader sync (kosync protocol)
            "/api/koreader/devices" bind Method.POST to
                authFilter.then(
                    koReaderSyncHandler?.let { it::register } ?: { _ ->
                        Response(Status.SERVICE_UNAVAILABLE)
                    },
                ),
            "/api/koreader/devices" bind Method.GET to
                authFilter.then(
                    koReaderSyncHandler?.let { it::listDevices } ?: { _ ->
                        Response(Status.SERVICE_UNAVAILABLE)
                    },
                ),
            "/api/koreader/devices/{token}" bind Method.DELETE to
                authFilter.then(
                    koReaderSyncHandler?.let { it::deleteDevice } ?: { _ ->
                        Response(Status.SERVICE_UNAVAILABLE)
                    },
                ),
            "/koreader/{token}/syncs/progress" bind Method.PUT to (
                koReaderSyncHandler?.let { it::pushProgress } ?: { _ ->
                    Response(Status.SERVICE_UNAVAILABLE)
                }
            ),
            "/koreader/{token}/syncs/progress/{document}" bind Method.GET to (
                koReaderSyncHandler?.let { it::getProgress } ?: { _ ->
                    Response(Status.SERVICE_UNAVAILABLE)
                }
            ),
            // Komga-compatible API (for Tachiyomi / Paperback)
            "/api/v1/libraries" bind Method.GET to
                authFilter.then(
                    komgaApiHandler?.let { it::libraries } ?: { _ ->
                        Response(Status.SERVICE_UNAVAILABLE)
                    },
                ),
            "/api/v1/series" bind Method.GET to
                authFilter.then(
                    komgaApiHandler?.let { it::series } ?: { _ ->
                        Response(Status.SERVICE_UNAVAILABLE)
                    },
                ),
            "/api/v1/series/{id}" bind Method.GET to
                authFilter.then(
                    komgaApiHandler?.let { it::seriesById } ?: { _ ->
                        Response(Status.SERVICE_UNAVAILABLE)
                    },
                ),
            "/api/v1/books" bind Method.GET to
                authFilter.then(
                    komgaApiHandler?.let { it::books } ?: { _ ->
                        Response(Status.SERVICE_UNAVAILABLE)
                    },
                ),
            "/api/v1/books/{id}" bind Method.GET to
                authFilter.then(
                    komgaApiHandler?.let { it::bookById } ?: { _ ->
                        Response(Status.SERVICE_UNAVAILABLE)
                    },
                ),
            // Comic/manga reading direction
            "/api/books/{id}/reading-direction" bind Method.PUT to authFilter.then(::setReadingDirection),
            // Multi-author support
            "/api/books/{id}/authors" bind Method.PUT to authFilter.then(::setAuthors),
            // Categories
            "/api/books/{id}/categories" bind Method.PUT to authFilter.then(::setCategories),
            // Moods and extended metadata
            "/api/books/{id}/moods" bind Method.PUT to authFilter.then(::setMoods),
            "/api/books/{id}/extended-metadata" bind Method.PUT to authFilter.then(::setExtendedMetadata),
            // External IDs
            "/api/books/{id}/external-ids" bind Method.PUT to authFilter.then(::setExternalIds),
            // Metadata locks
            "/api/books/{id}/metadata-locks" bind Method.GET to authFilter.then(::getMetadataLocks),
            "/api/books/{id}/metadata-locks" bind Method.PUT to authFilter.then(::setMetadataLocks),
            // Reading statistics
            "/api/stats/reading" bind Method.GET to authFilter.then(::getReadingStats),
            // Audiobook listening sessions and statistics
            "/api/books/{id}/listen" bind Method.POST to authFilter.then(::recordListenSession),
            "/api/books/{id}/listen-progress" bind Method.GET to authFilter.then(::getListenProgress),
            "/api/books/{id}/listen-progress" bind Method.PUT to authFilter.then(::updateListenProgress),
            "/api/stats/listening" bind Method.GET to authFilter.then(::getListeningStats),
            "/api/listen-sessions" bind Method.GET to authFilter.then(::getRecentListenSessions),
            // Custom fonts for EPUB reader
            "/api/fonts" bind Method.GET to authFilter.then(fontHandler?.let { it::list } ?: { _ -> Response(Status.SERVICE_UNAVAILABLE) }),
            "/api/fonts" bind Method.POST to
                authFilter.then(
                    fontHandler?.let { it::upload } ?: { _ ->
                        Response(Status.SERVICE_UNAVAILABLE)
                    },
                ),
            "/api/fonts/{id}" bind Method.DELETE to
                authFilter.then(
                    fontHandler?.let { it::delete } ?: { _ ->
                        Response(Status.SERVICE_UNAVAILABLE)
                    },
                ),
            "/fonts/{userId}/{filename}" bind Method.GET to (fontHandler?.let { it::serve } ?: { _ -> Response(Status.NOT_FOUND) }),
            // Per-format reader preferences
            "/api/reader-preferences/{format}" bind Method.GET to
                authFilter.then(
                    readerPreferencesHandler?.let { it::get } ?: { _ ->
                        Response(Status.SERVICE_UNAVAILABLE)
                    },
                ),
            "/api/reader-preferences/{format}" bind Method.PUT to
                authFilter.then(
                    readerPreferencesHandler?.let { it::set } ?: { _ ->
                        Response(Status.SERVICE_UNAVAILABLE)
                    },
                ),
            "/api/reader-preferences/{format}" bind Method.PATCH to
                authFilter.then(
                    readerPreferencesHandler?.let { it::merge } ?: { _ ->
                        Response(Status.SERVICE_UNAVAILABLE)
                    },
                ),
            "/api/reader-preferences/{format}" bind Method.DELETE to
                authFilter.then(
                    readerPreferencesHandler?.let { it::delete } ?: { _ ->
                        Response(Status.SERVICE_UNAVAILABLE)
                    },
                ),
        )

    /** PUT /api/books/{id}/reading-direction — set comic/manga reading direction (ltr or rtl). */
    private fun setReadingDirection(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val bookId =
            req.uri.path
                .split("/")
                .filter { it.isNotBlank() }
                .let { parts -> parts.getOrNull(parts.indexOf("books") + 1) }
                ?.let { runCatching { java.util.UUID.fromString(it) }.getOrNull() }
                ?: return Response(Status.BAD_REQUEST)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"Invalid book ID"}""")
        val body =
            runCatching {
                org.booktower.config.Json.mapper
                    .readTree(req.bodyString())
            }.getOrNull()
        val direction =
            body?.get("direction")?.asText()?.takeIf { it.isNotBlank() }
                ?: return Response(Status.BAD_REQUEST)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"Missing direction field (ltr or rtl)"}""")
        val updated = bookService.setReadingDirection(userId, bookId, direction)
        return if (updated) {
            Response(Status.OK)
                .header("Content-Type", "application/json")
                .body("""{"direction":"${if (direction.lowercase() == "rtl") "rtl" else "ltr"}"}""")
        } else {
            Response(Status.NOT_FOUND)
                .header("Content-Type", "application/json")
                .body("""{"error":"Book not found"}""")
        }
    }

    /** GET /api/stats/reading — returns reading statistics for the authenticated user */
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

    /** POST /api/books/{id}/listen — record a listening session (startPosSec, endPosSec, totalSec?) */
    private fun recordListenSession(req: Request): Response {
        val svc = listeningSessionService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val userId = AuthenticatedUser.from(req)
        val bookId =
            extractBookIdFromPath(req)
                ?: return Response(Status.BAD_REQUEST).header("Content-Type", "application/json").body("""{"error":"Invalid book ID"}""")
        val body =
            runCatching {
                org.booktower.config.Json.mapper
                    .readTree(req.bodyString())
            }.getOrNull()
                ?: return Response(Status.BAD_REQUEST).header("Content-Type", "application/json").body("""{"error":"Invalid JSON"}""")
        val startPos = body.get("startPosSec")?.asInt() ?: 0
        val endPos =
            body.get("endPosSec")?.asInt()
                ?: return Response(Status.BAD_REQUEST).header("Content-Type", "application/json").body("""{"error":"endPosSec required"}""")
        val totalSec = body.get("totalSec")?.takeIf { !it.isNull }?.asInt()
        svc.recordSession(userId, bookId, startPos, endPos, totalSec)
        return Response(Status.NO_CONTENT)
    }

    /** GET /api/books/{id}/listen-progress — get current listening position */
    private fun getListenProgress(req: Request): Response {
        val svc = listeningSessionService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val userId = AuthenticatedUser.from(req)
        val bookId =
            extractBookIdFromPath(req)
                ?: return Response(Status.BAD_REQUEST).header("Content-Type", "application/json").body("""{"error":"Invalid book ID"}""")
        val progress =
            svc.getProgress(userId, bookId)
                ?: return Response(Status.NOT_FOUND).header("Content-Type", "application/json").body("""{"error":"No progress found"}""")
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                org.booktower.config.Json.mapper
                    .writeValueAsString(progress),
            )
    }

    /** PUT /api/books/{id}/listen-progress — update position without creating a session */
    private fun updateListenProgress(req: Request): Response {
        val svc = listeningSessionService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val userId = AuthenticatedUser.from(req)
        val bookId =
            extractBookIdFromPath(req)
                ?: return Response(Status.BAD_REQUEST).header("Content-Type", "application/json").body("""{"error":"Invalid book ID"}""")
        val body =
            runCatching {
                org.booktower.config.Json.mapper
                    .readTree(req.bodyString())
            }.getOrNull()
                ?: return Response(Status.BAD_REQUEST).header("Content-Type", "application/json").body("""{"error":"Invalid JSON"}""")
        val positionSec =
            body.get("positionSec")?.asInt()
                ?: return Response(
                    Status.BAD_REQUEST,
                ).header("Content-Type", "application/json").body("""{"error":"positionSec required"}""")
        val totalSec = body.get("totalSec")?.takeIf { !it.isNull }?.asInt()
        svc.updateProgress(userId, bookId, positionSec, totalSec)
        return Response(Status.NO_CONTENT)
    }

    /** GET /api/stats/listening — returns listening statistics for the authenticated user */
    private fun getListeningStats(req: Request): Response {
        val svc = listeningStatsService ?: return Response(Status.SERVICE_UNAVAILABLE)
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

    /** GET /api/listen-sessions — returns recent listening sessions for the authenticated user */
    private fun getRecentListenSessions(req: Request): Response {
        val svc = listeningSessionService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val userId = AuthenticatedUser.from(req)
        val limit = req.query("limit")?.toIntOrNull()?.coerceIn(1, 100) ?: 20
        val sessions = svc.getRecentSessions(userId, limit)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                org.booktower.config.Json.mapper
                    .writeValueAsString(sessions),
            )
    }

    private fun extractBookIdFromPath(req: Request): java.util.UUID? =
        req.uri.path
            .split("/")
            .filter { it.isNotBlank() }
            .let { parts -> parts.getOrNull(parts.indexOf("books") + 1) }
            ?.let { runCatching { java.util.UUID.fromString(it) }.getOrNull() }

    /** GET /api/books/{id}/metadata-locks — returns list of locked field names */
    private fun getMetadataLocks(req: Request): Response {
        val svc = metadataLockService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val bookId =
            extractBookIdFromPath(req)
                ?: return Response(Status.BAD_REQUEST).header("Content-Type", "application/json").body("""{"error":"Invalid book ID"}""")
        val locked = svc.getLockedFields(bookId).sorted()
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                org.booktower.config.Json.mapper
                    .writeValueAsString(mapOf("lockedFields" to locked)),
            )
    }

    /** PUT /api/books/{id}/metadata-locks — replaces locked field set. Body: {"lockedFields":["title","author"]} */
    private fun setMetadataLocks(req: Request): Response {
        val svc = metadataLockService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val bookId =
            extractBookIdFromPath(req)
                ?: return Response(Status.BAD_REQUEST).header("Content-Type", "application/json").body("""{"error":"Invalid book ID"}""")
        val body =
            runCatching {
                org.booktower.config.Json.mapper
                    .readTree(req.bodyString())
            }.getOrNull()
                ?: return Response(
                    Status.BAD_REQUEST,
                ).header("Content-Type", "application/json").body("""{"error":"Request body required"}""")
        val fields =
            body
                .get("lockedFields")
                ?.takeIf { it.isArray }
                ?.map { it.asText() }
                ?.filter { it.isNotBlank() } ?: emptyList()
        svc.setLockedFields(bookId, fields)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                org.booktower.config.Json.mapper
                    .writeValueAsString(mapOf("lockedFields" to fields)),
            )
    }

    /** PUT /api/books/{id}/external-ids — set external IDs (Goodreads, Hardcover, etc.) */
    private fun setExternalIds(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val bookId =
            req.uri.path
                .split("/")
                .filter { it.isNotBlank() }
                .let { parts -> parts.getOrNull(parts.indexOf("books") + 1) }
                ?.let { runCatching { java.util.UUID.fromString(it) }.getOrNull() }
                ?: return Response(Status.BAD_REQUEST).header("Content-Type", "application/json").body("""{"error":"Invalid book ID"}""")
        val body =
            runCatching {
                org.booktower.config.Json.mapper
                    .readTree(req.bodyString())
            }.getOrNull()
                ?: return Response(
                    Status.BAD_REQUEST,
                ).header("Content-Type", "application/json").body("""{"error":"Request body required"}""")

        fun str(key: String) = body.get(key)?.asText()?.takeIf { it.isNotBlank() }
        val updated =
            bookService.updateExternalIds(
                userId,
                bookId,
                str("goodreadsId"),
                str("hardcoverId"),
                str("comicvineId"),
                str("openlibraryId"),
                str("googleBooksId"),
                str("amazonId"),
                str("audibleId"),
            )
        return if (updated) {
            val book = bookService.getBook(userId, bookId) ?: return Response(Status.NOT_FOUND)
            Response(Status.OK)
                .header("Content-Type", "application/json")
                .body(
                    org.booktower.config.Json.mapper
                        .writeValueAsString(book),
                )
        } else {
            Response(Status.NOT_FOUND).header("Content-Type", "application/json").body("""{"error":"Book not found"}""")
        }
    }

    /** PUT /api/books/{id}/moods — replace moods for a book. Body: {"moods":["dark","hopeful"]} */
    private fun setMoods(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val bookId =
            req.uri.path
                .split("/")
                .filter { it.isNotBlank() }
                .let { parts -> parts.getOrNull(parts.indexOf("books") + 1) }
                ?.let { runCatching { java.util.UUID.fromString(it) }.getOrNull() }
                ?: return Response(Status.BAD_REQUEST).header("Content-Type", "application/json").body("""{"error":"Invalid book ID"}""")
        val body =
            runCatching {
                org.booktower.config.Json.mapper
                    .readTree(req.bodyString())
            }.getOrNull()
        val moods =
            body
                ?.get("moods")
                ?.takeIf { it.isArray }
                ?.map { it.asText() }
                ?.filter { it.isNotBlank() }
                ?: return Response(
                    Status.BAD_REQUEST,
                ).header("Content-Type", "application/json").body("""{"error":"moods array is required"}""")
        val updated = bookService.setMoods(userId, bookId, moods)
        return if (updated) {
            Response(Status.OK)
                .header("Content-Type", "application/json")
                .body(
                    org.booktower.config.Json.mapper
                        .writeValueAsString(mapOf("moods" to moods)),
                )
        } else {
            Response(Status.NOT_FOUND).header("Content-Type", "application/json").body("""{"error":"Book not found"}""")
        }
    }

    /** PUT /api/books/{id}/extended-metadata — update subtitle, language, content_rating, age_rating */
    private fun setExtendedMetadata(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val bookId =
            req.uri.path
                .split("/")
                .filter { it.isNotBlank() }
                .let { parts -> parts.getOrNull(parts.indexOf("books") + 1) }
                ?.let { runCatching { java.util.UUID.fromString(it) }.getOrNull() }
                ?: return Response(Status.BAD_REQUEST).header("Content-Type", "application/json").body("""{"error":"Invalid book ID"}""")
        val body =
            runCatching {
                org.booktower.config.Json.mapper
                    .readTree(req.bodyString())
            }.getOrNull()
                ?: return Response(
                    Status.BAD_REQUEST,
                ).header("Content-Type", "application/json").body("""{"error":"Request body required"}""")
        val subtitle = body.get("subtitle")?.asText()?.takeIf { it.isNotBlank() }
        val language = body.get("language")?.asText()?.takeIf { it.isNotBlank() }
        val contentRating = body.get("contentRating")?.asText()?.takeIf { it.isNotBlank() }
        val ageRating = body.get("ageRating")?.asText()?.takeIf { it.isNotBlank() }
        val updated = bookService.updateExtendedMetadata(userId, bookId, subtitle, language, contentRating, ageRating)
        return if (updated) {
            val book = bookService.getBook(userId, bookId) ?: return Response(Status.NOT_FOUND)
            Response(Status.OK)
                .header("Content-Type", "application/json")
                .body(
                    org.booktower.config.Json.mapper
                        .writeValueAsString(book),
                )
        } else {
            Response(Status.NOT_FOUND).header("Content-Type", "application/json").body("""{"error":"Book not found"}""")
        }
    }

    /** PUT /api/books/{id}/categories — replace the full category list for a book. Body: {"categories":["Fiction","Mystery"]} */
    private fun setCategories(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val bookId =
            req.uri.path
                .split("/")
                .filter { it.isNotBlank() }
                .let { parts -> parts.getOrNull(parts.indexOf("books") + 1) }
                ?.let { runCatching { java.util.UUID.fromString(it) }.getOrNull() }
                ?: return Response(Status.BAD_REQUEST)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"Invalid book ID"}""")
        val body =
            runCatching {
                org.booktower.config.Json.mapper
                    .readTree(req.bodyString())
            }.getOrNull()
        val categories =
            body
                ?.get("categories")
                ?.takeIf { it.isArray }
                ?.map { it.asText() }
                ?.filter { it.isNotBlank() }
                ?: return Response(Status.BAD_REQUEST)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"categories array is required"}""")
        val updated = bookService.setCategories(userId, bookId, categories)
        return if (updated) {
            Response(Status.OK)
                .header("Content-Type", "application/json")
                .body(
                    org.booktower.config.Json.mapper
                        .writeValueAsString(mapOf("categories" to categories)),
                )
        } else {
            Response(Status.NOT_FOUND)
                .header("Content-Type", "application/json")
                .body("""{"error":"Book not found"}""")
        }
    }

    /** PUT /api/books/{id}/authors — replace the full author list for a book. Body: {"authors":["Alice","Bob"]} */
    private fun setAuthors(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val bookId =
            req.uri.path
                .split("/")
                .filter { it.isNotBlank() }
                .let { parts -> parts.getOrNull(parts.indexOf("books") + 1) }
                ?.let { runCatching { java.util.UUID.fromString(it) }.getOrNull() }
                ?: return Response(Status.BAD_REQUEST)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"Invalid book ID"}""")
        val body =
            runCatching {
                org.booktower.config.Json.mapper
                    .readTree(req.bodyString())
            }.getOrNull()
        val authors =
            body
                ?.get("authors")
                ?.takeIf { it.isArray }
                ?.map { it.asText() }
                ?.filter { it.isNotBlank() }
                ?: return Response(Status.BAD_REQUEST)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"authors array is required"}""")
        val updated = bookService.setAuthors(userId, bookId, authors)
        return if (updated) {
            Response(Status.OK)
                .header("Content-Type", "application/json")
                .body(
                    org.booktower.config.Json.mapper
                        .writeValueAsString(mapOf("authors" to authors)),
                )
        } else {
            Response(Status.NOT_FOUND)
                .header("Content-Type", "application/json")
                .body("""{"error":"Book not found"}""")
        }
    }

    /** POST /api/books/{id}/apply-sidecar-metadata — load metadata from .opf/.nfo file next to the book */
    private fun applySidecarMetadata(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val bookId =
            req.uri.path
                .split("/")
                .filter { it.isNotBlank() }
                .let { parts -> parts.getOrNull(parts.indexOf("books") + 1) }
                ?.let { runCatching { java.util.UUID.fromString(it) }.getOrNull() }
                ?: return Response(Status.BAD_REQUEST)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"Invalid book ID"}""")
        val book =
            bookService.getBook(userId, bookId)
                ?: return Response(Status.NOT_FOUND)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"Book not found"}""")
        val sidecar =
            org.booktower.services.SidecarMetadataService
                .read(book.filePath ?: "")
                ?: return Response(Status.NOT_FOUND)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"No sidecar file found"}""")
        val updated =
            bookService.applyFetchedMetadata(userId, bookId, sidecar)
                ?: return Response(Status.NOT_FOUND)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"Book not found"}""")
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                org.booktower.config.Json.mapper.writeValueAsString(
                    mapOf("book" to updated, "source" to sidecar.source),
                ),
            )
    }

    /** POST /api/books/{id}/apply-filename-metadata — parse title/author/series from the book's filename */
    private fun applyFilenameMetadata(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val bookId =
            req.uri.path
                .split("/")
                .filter { it.isNotBlank() }
                .let { parts -> parts.getOrNull(parts.indexOf("books") + 1) }
                ?.let { runCatching { java.util.UUID.fromString(it) }.getOrNull() }
                ?: return Response(Status.BAD_REQUEST)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"Invalid book ID"}""")
        val book =
            bookService.getBook(userId, bookId)
                ?: return Response(Status.NOT_FOUND)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"Book not found"}""")
        val fileMeta =
            org.booktower.services.FilenameMetadataService
                .extract(book.filePath ?: book.title)
        val fetchedMeta =
            org.booktower.models.FetchedMetadata(
                title = fileMeta.title,
                author = fileMeta.author,
                description = null,
                isbn = null,
                publisher = null,
                publishedDate = null,
                source = "filename",
            )
        val updated =
            bookService.applyFetchedMetadata(userId, bookId, fetchedMeta)
                ?: return Response(Status.NOT_FOUND)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"Book not found"}""")
        // Also persist series if extracted
        if (fileMeta.series != null) {
            bookService.updateSeries(userId, bookId, fileMeta.series, fileMeta.seriesIndex)
        }
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                org.booktower.config.Json.mapper.writeValueAsString(
                    mapOf("book" to updated, "extracted" to fileMeta),
                ),
            )
    }

    // ─── Email providers (admin) ─────────────────────────────────────────────

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

    // ─── Scheduled tasks (admin) ─────────────────────────────────────────────

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

    // ─── Book notebooks ───────────────────────────────────────────────────────

    private fun listNotebooks(req: Request): Response {
        val svc = bookNotebookService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val userId = AuthenticatedUser.from(req)
        val bookId =
            req.uri.path
                .split("/")
                .dropLast(1)
                .lastOrNull() ?: return Response(Status.BAD_REQUEST)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                org.booktower.config.Json.mapper
                    .writeValueAsString(svc.listForBook(bookId, userId)),
            )
    }

    private fun createNotebook(req: Request): Response {
        val svc = bookNotebookService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val userId = AuthenticatedUser.from(req)
        val bookId =
            req.uri.path
                .split("/")
                .dropLast(1)
                .lastOrNull() ?: return Response(Status.BAD_REQUEST)
        val request =
            runCatching {
                org.booktower.config.Json.mapper
                    .readValue(req.bodyString(), CreateNotebookRequest::class.java)
            }.getOrNull() ?: return Response(Status.BAD_REQUEST).body("""{"error":"Invalid request body"}""")
        return runCatching { svc.create(bookId, userId, request) }
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

    private fun getNotebook(req: Request): Response {
        val svc = bookNotebookService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val userId = AuthenticatedUser.from(req)
        val parts =
            req.uri.path
                .split("/")
                .filter { it.isNotBlank() }
        val notebookId = parts.lastOrNull() ?: return Response(Status.BAD_REQUEST)
        val bookId = parts.dropLast(2).lastOrNull() ?: return Response(Status.BAD_REQUEST)
        val nb = svc.get(bookId, notebookId, userId) ?: return Response(Status.NOT_FOUND)
        return Response(Status.OK).header("Content-Type", "application/json").body(
            org.booktower.config.Json.mapper
                .writeValueAsString(nb),
        )
    }

    private fun updateNotebook(req: Request): Response {
        val svc = bookNotebookService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val userId = AuthenticatedUser.from(req)
        val parts =
            req.uri.path
                .split("/")
                .filter { it.isNotBlank() }
        val notebookId = parts.lastOrNull() ?: return Response(Status.BAD_REQUEST)
        val bookId = parts.dropLast(2).lastOrNull() ?: return Response(Status.BAD_REQUEST)
        val request =
            runCatching {
                org.booktower.config.Json.mapper
                    .readValue(req.bodyString(), UpdateNotebookRequest::class.java)
            }.getOrNull() ?: return Response(Status.BAD_REQUEST).body("""{"error":"Invalid request body"}""")
        val updated =
            runCatching { svc.update(bookId, notebookId, userId, request) }
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

    private fun deleteNotebook(req: Request): Response {
        val svc = bookNotebookService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val userId = AuthenticatedUser.from(req)
        val parts =
            req.uri.path
                .split("/")
                .filter { it.isNotBlank() }
        val notebookId = parts.lastOrNull() ?: return Response(Status.BAD_REQUEST)
        val bookId = parts.dropLast(2).lastOrNull() ?: return Response(Status.BAD_REQUEST)
        return if (svc.delete(bookId, notebookId, userId)) Response(Status.NO_CONTENT) else Response(Status.NOT_FOUND)
    }

    // ─── Book reviews ─────────────────────────────────────────────────────────

    private fun listReviews(req: Request): Response {
        val svc = bookReviewService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val bookId =
            req.uri.path
                .split("/")
                .dropLast(1)
                .lastOrNull() ?: return Response(Status.BAD_REQUEST)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                org.booktower.config.Json.mapper
                    .writeValueAsString(svc.listForBook(bookId)),
            )
    }

    private fun createReview(req: Request): Response {
        val svc = bookReviewService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val userId = AuthenticatedUser.from(req)
        val bookId =
            req.uri.path
                .split("/")
                .dropLast(1)
                .lastOrNull() ?: return Response(Status.BAD_REQUEST)
        val request =
            runCatching {
                org.booktower.config.Json.mapper
                    .readValue(req.bodyString(), CreateReviewRequest::class.java)
            }.getOrNull() ?: return Response(Status.BAD_REQUEST).body("""{"error":"Invalid request body"}""")
        return runCatching { svc.create(bookId, userId, request) }
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

    private fun updateReview(req: Request): Response {
        val svc = bookReviewService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val userId = AuthenticatedUser.from(req)
        val parts =
            req.uri.path
                .split("/")
                .filter { it.isNotBlank() }
        val reviewId = parts.lastOrNull() ?: return Response(Status.BAD_REQUEST)
        val bookId = parts.dropLast(2).lastOrNull() ?: return Response(Status.BAD_REQUEST)
        val request =
            runCatching {
                org.booktower.config.Json.mapper
                    .readValue(req.bodyString(), UpdateReviewRequest::class.java)
            }.getOrNull() ?: return Response(Status.BAD_REQUEST).body("""{"error":"Invalid request body"}""")
        val updated =
            runCatching { svc.update(bookId, reviewId, userId, request) }
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

    private fun deleteReview(req: Request): Response {
        val svc = bookReviewService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val userId = AuthenticatedUser.from(req)
        val parts =
            req.uri.path
                .split("/")
                .filter { it.isNotBlank() }
        val reviewId = parts.lastOrNull() ?: return Response(Status.BAD_REQUEST)
        val bookId = parts.dropLast(2).lastOrNull() ?: return Response(Status.BAD_REQUEST)
        return if (svc.delete(bookId, reviewId, userId)) Response(Status.NO_CONTENT) else Response(Status.NOT_FOUND)
    }

    // ─── Content restrictions ─────────────────────────────────────────────────

    /** GET /api/user/content-restrictions */
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

    /** PUT /api/user/content-restrictions */
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

    // ─── Comic metadata ───────────────────────────────────────────────────────

    /** GET /api/books/{id}/comic-metadata */
    private fun getComicMetadata(req: Request): Response {
        val svc = comicMetadataService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val bookId =
            req.uri.path
                .split("/")
                .dropLast(1)
                .lastOrNull() ?: return Response(Status.BAD_REQUEST)
        val meta = svc.get(bookId)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                org.booktower.config.Json.mapper
                    .writeValueAsString(meta),
            )
    }

    /** PUT /api/books/{id}/comic-metadata */
    private fun updateComicMetadata(req: Request): Response {
        val svc = comicMetadataService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val bookId =
            req.uri.path
                .split("/")
                .dropLast(1)
                .lastOrNull() ?: return Response(Status.BAD_REQUEST)
        val request =
            runCatching {
                org.booktower.config.Json.mapper
                    .readValue(req.bodyString(), org.booktower.models.ComicMetadataRequest::class.java)
            }.getOrNull() ?: return Response(Status.BAD_REQUEST).body("""{"error":"Invalid request body"}""")
        val updated = svc.update(bookId, request)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                org.booktower.config.Json.mapper
                    .writeValueAsString(updated),
            )
    }

    /** POST /api/covers/regenerate?libraryId={id} — bulk-submit cover extraction */
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

    private fun libraryHealth(req: Request): Response {
        val svc =
            libraryHealthService
                ?: return Response(Status.SERVICE_UNAVAILABLE)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"Library health service not available"}""")
        val userId = AuthenticatedUser.from(req)
        val report = svc.check(userId)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                org.booktower.config.Json.mapper
                    .writeValueAsString(report),
            )
    }

    /** GET /api/metadata/search?title=...&author=...&source=... */
    private fun metadataSearch(req: Request): Response {
        val title =
            req.query("title")?.takeIf { it.isNotBlank() }
                ?: return Response(Status.BAD_REQUEST)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"title is required"}""")
        val author = req.query("author")?.takeIf { it.isNotBlank() }
        val source = req.query("source")?.takeIf { it.isNotBlank() }
        val result = metadataFetchService.fetchMetadata(title, author, source)
        return if (result != null) {
            Response(Status.OK)
                .header("Content-Type", "application/json")
                .body(
                    org.booktower.config.Json.mapper
                        .writeValueAsString(result),
                )
        } else {
            Response(Status.NOT_FOUND)
                .header("Content-Type", "application/json")
                .body("""{"error":"No metadata found"}""")
        }
    }

    /** GET /api/bookdrop — list files waiting in the drop folder */
    private fun bookDropList(req: Request): Response {
        val svc =
            bookDropService
                ?: return Response(Status.SERVICE_UNAVAILABLE)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"BookDrop service not available"}""")
        val pending = svc.listPending()
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                org.booktower.config.Json.mapper
                    .writeValueAsString(mapOf("files" to pending)),
            )
    }

    /** POST /api/bookdrop/{filename}/import — import a dropped file into a library */
    private fun bookDropImport(req: Request): Response {
        val svc =
            bookDropService
                ?: return Response(Status.SERVICE_UNAVAILABLE)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"BookDrop service not available"}""")
        val userId = AuthenticatedUser.from(req)
        val filename =
            req.uri.path
                .split("/")
                .let { parts ->
                    val idx = parts.indexOf("bookdrop")
                    if (idx >= 0 && idx + 1 < parts.size) parts[idx + 1] else null
                }?.let { java.net.URLDecoder.decode(it, "UTF-8") }
                ?: return Response(Status.BAD_REQUEST)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"filename is required"}""")
        val body =
            runCatching {
                org.booktower.config.Json.mapper
                    .readTree(req.bodyString())
            }.getOrNull()
        val libraryId =
            body?.get("libraryId")?.asText()?.takeIf { it.isNotBlank() }
                ?: return Response(Status.BAD_REQUEST)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"libraryId is required"}""")
        // Verify the user owns the library
        val libUuid =
            runCatching { java.util.UUID.fromString(libraryId) }.getOrNull()
                ?: return Response(Status.BAD_REQUEST)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"Invalid library ID"}""")
        val library =
            libraryService.getLibrary(userId, libUuid)
                ?: return Response(Status.NOT_FOUND)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"Library not found"}""")
        val bookId =
            svc.import(userId, filename, libraryId, library.path)
                ?: return Response(Status.NOT_FOUND)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"File not found in drop folder"}""")
        return Response(Status.CREATED)
            .header("Content-Type", "application/json")
            .body(
                org.booktower.config.Json.mapper
                    .writeValueAsString(mapOf("bookId" to bookId)),
            )
    }

    /** DELETE /api/bookdrop/{filename} — discard a dropped file */
    private fun bookDropDiscard(req: Request): Response {
        val svc =
            bookDropService
                ?: return Response(Status.SERVICE_UNAVAILABLE)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"BookDrop service not available"}""")
        val filename =
            req.uri.path
                .split("/")
                .lastOrNull()
                ?.let { java.net.URLDecoder.decode(it, "UTF-8") }
                ?: return Response(Status.BAD_REQUEST)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"filename is required"}""")
        return if (svc.discard(filename)) {
            Response(Status.NO_CONTENT)
        } else {
            Response(Status.NOT_FOUND).header("Content-Type", "application/json").body("""{"error":"File not found"}""")
        }
    }

    /** POST /api/books/{id}/send — send the book file to an email address */
    private fun sendBook(req: Request): Response {
        val svc =
            bookDeliveryService
                ?: return Response(Status.SERVICE_UNAVAILABLE)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"Book delivery service not available"}""")
        val userId = AuthenticatedUser.from(req)
        val bookId =
            req.uri.path
                .split("/")
                .filter { it.isNotBlank() }
                .let { parts -> parts.getOrNull(parts.indexOf("books") + 1) }
                ?.let { runCatching { java.util.UUID.fromString(it) }.getOrNull() }
                ?: return Response(Status.BAD_REQUEST)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"Invalid book ID"}""")
        val body =
            runCatching {
                org.booktower.config.Json.mapper
                    .readTree(req.bodyString())
            }.getOrNull()
        val toEmail =
            body?.get("email")?.asText()?.takeIf { it.isNotBlank() }
                ?: return Response(Status.BAD_REQUEST)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"email is required"}""")
        return try {
            svc.sendBook(userId, bookId, toEmail)
            Response(Status.OK)
                .header("Content-Type", "application/json")
                .body("""{"sent":true}""")
        } catch (e: IllegalArgumentException) {
            Response(Status.BAD_REQUEST)
                .header("Content-Type", "application/json")
                .body(
                    org.booktower.config.Json.mapper
                        .writeValueAsString(mapOf("error" to e.message)),
                )
        }
    }

    /** GET /api/delivery/recipients */
    private fun listRecipients(req: Request): Response {
        val svc =
            bookDeliveryService
                ?: return Response(Status.SERVICE_UNAVAILABLE)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"Book delivery service not available"}""")
        val userId = AuthenticatedUser.from(req)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                org.booktower.config.Json.mapper
                    .writeValueAsString(mapOf("recipients" to svc.listRecipients(userId))),
            )
    }

    /** POST /api/delivery/recipients */
    private fun addRecipient(req: Request): Response {
        val svc =
            bookDeliveryService
                ?: return Response(Status.SERVICE_UNAVAILABLE)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"Book delivery service not available"}""")
        val userId = AuthenticatedUser.from(req)
        val body =
            runCatching {
                org.booktower.config.Json.mapper
                    .readTree(req.bodyString())
            }.getOrNull()
        val label =
            body?.get("label")?.asText()?.takeIf { it.isNotBlank() }
                ?: return Response(Status.BAD_REQUEST)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"label is required"}""")
        val email =
            body.get("email")?.asText()?.takeIf { it.isNotBlank() }
                ?: return Response(Status.BAD_REQUEST)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"email is required"}""")
        return try {
            val recipient = svc.addRecipient(userId, org.booktower.services.AddRecipientRequest(label, email))
            Response(Status.CREATED)
                .header("Content-Type", "application/json")
                .body(
                    org.booktower.config.Json.mapper
                        .writeValueAsString(recipient),
                )
        } catch (e: IllegalArgumentException) {
            Response(Status.BAD_REQUEST)
                .header("Content-Type", "application/json")
                .body(
                    org.booktower.config.Json.mapper
                        .writeValueAsString(mapOf("error" to e.message)),
                )
        }
    }

    /** DELETE /api/delivery/recipients/{id} */
    private fun deleteRecipient(req: Request): Response {
        val svc =
            bookDeliveryService
                ?: return Response(Status.SERVICE_UNAVAILABLE)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"Book delivery service not available"}""")
        val userId = AuthenticatedUser.from(req)
        val recipientId =
            req.uri.path
                .split("/")
                .lastOrNull()
                ?: return Response(Status.BAD_REQUEST)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"Invalid recipient ID"}""")
        return if (svc.deleteRecipient(userId, recipientId)) {
            Response(Status.NO_CONTENT)
        } else {
            Response(Status.NOT_FOUND).header("Content-Type", "application/json").body("""{"error":"Recipient not found"}""")
        }
    }

    /** GET /api/books/{id}/covers/alternatives — list candidate cover URLs from OpenLibrary + Google Books */
    private fun alternativeCovers(req: Request): Response {
        val svc =
            alternativeCoverService
                ?: return Response(Status.SERVICE_UNAVAILABLE)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"Alternative cover service not available"}""")
        val userId = AuthenticatedUser.from(req)
        val bookId =
            req.uri.path
                .split("/")
                .filter { it.isNotBlank() }
                .let { parts -> parts.getOrNull(parts.indexOf("books") + 1) }
                ?.let { runCatching { java.util.UUID.fromString(it) }.getOrNull() }
                ?: return Response(Status.BAD_REQUEST)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"Invalid book ID"}""")
        val book =
            bookService.getBook(userId, bookId)
                ?: return Response(Status.NOT_FOUND)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"Book not found"}""")
        val candidates = svc.fetchCandidates(book.title, book.author, book.isbn)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                org.booktower.config.Json.mapper
                    .writeValueAsString(mapOf("covers" to candidates)),
            )
    }

    /** POST /api/books/{id}/cover/apply-url — download a cover image from a URL and save it */
    private fun applyCoverUrl(req: Request): Response {
        val svc =
            alternativeCoverService
                ?: return Response(Status.SERVICE_UNAVAILABLE)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"Alternative cover service not available"}""")
        val userId = AuthenticatedUser.from(req)
        val bookId =
            req.uri.path
                .split("/")
                .filter { it.isNotBlank() }
                .let { parts -> parts.getOrNull(parts.indexOf("books") + 1) }
                ?.let { runCatching { java.util.UUID.fromString(it) }.getOrNull() }
                ?: return Response(Status.BAD_REQUEST)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"Invalid book ID"}""")
        bookService.getBook(userId, bookId)
            ?: return Response(Status.NOT_FOUND)
                .header("Content-Type", "application/json")
                .body("""{"error":"Book not found"}""")
        val body =
            runCatching {
                org.booktower.config.Json.mapper
                    .readTree(req.bodyString())
            }.getOrNull()
        val url =
            body?.get("url")?.asText()?.takeIf { it.isNotBlank() }
                ?: return Response(Status.BAD_REQUEST)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"url is required"}""")
        // Derive extension from URL; default to jpg
        val ext =
            url
                .substringAfterLast('.', "jpg")
                .lowercase()
                .takeIf { it in setOf("jpg", "jpeg", "png", "webp") } ?: "jpg"
        val bytes =
            svc.downloadBytes(url)
                ?: return Response(Status.BAD_GATEWAY)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"Failed to download cover from URL"}""")
        val coversDir = java.io.File(storageConfig.coversPath)
        if (!coversDir.exists() && !coversDir.mkdirs()) logger.warn("Could not create covers directory: ${coversDir.absolutePath}")
        val coverFilename = "$bookId.$ext"
        java.io.File(coversDir, coverFilename).writeBytes(bytes)
        bookService.updateCoverPath(userId, bookId, coverFilename)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                org.booktower.config.Json.mapper
                    .writeValueAsString(mapOf("coverUrl" to "/covers/$coverFilename")),
            )
    }

    /** GET /api/authors/{name}/metadata — fetch author bio and photo from OpenLibrary */
    private fun authorMetadata(req: Request): Response {
        val svc =
            authorMetadataService
                ?: return Response(Status.SERVICE_UNAVAILABLE)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"Author metadata service not available"}""")
        val name =
            req.uri.path
                .split("/")
                .filter { it.isNotBlank() }
                .let { parts -> parts.getOrNull(parts.indexOf("authors") + 1) }
                ?.let { java.net.URLDecoder.decode(it, "UTF-8") }
                ?.takeIf { it.isNotBlank() }
                ?: return Response(Status.BAD_REQUEST)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"Author name is required"}""")
        val info =
            svc.fetch(name)
                ?: return Response(Status.NOT_FOUND)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"Author not found"}""")
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                org.booktower.config.Json.mapper
                    .writeValueAsString(info),
            )
    }

    /** GET /api/books/{id}/similar — find similar books in the user's library */
    private fun similarBooks(req: Request): Response {
        val svc =
            recommendationService
                ?: return Response(Status.SERVICE_UNAVAILABLE)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"Recommendation service not available"}""")
        val userId = AuthenticatedUser.from(req)
        val bookId =
            req.uri.path
                .split("/")
                .filter { it.isNotBlank() }
                .let { parts -> parts.getOrNull(parts.indexOf("books") + 1) }
                ?.let { runCatching { java.util.UUID.fromString(it) }.getOrNull() }
                ?: return Response(Status.BAD_REQUEST)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"Invalid book ID"}""")
        val similar = svc.findSimilar(userId, bookId)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                org.booktower.config.Json.mapper
                    .writeValueAsString(mapOf("similar" to similar)),
            )
    }

    /** GET /api/metadata/sources — lists available metadata provider keys */
    private fun metadataSources(req: Request): Response =
        Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                org.booktower.config.Json.mapper.writeValueAsString(
                    mapOf("sources" to org.booktower.services.METADATA_SOURCES),
                ),
            )

    /**
     * POST /api/books/{id}/metadata/propose
     * Fetches metadata from the given source (or all) and stores as a pending proposal.
     * Body: {"title":"...","author":"...","source":"openlibrary"} — title/author override book values.
     */
    private fun proposeMetadata(req: Request): Response {
        val svc = metadataProposalService ?: return Response(Status.SERVICE_UNAVAILABLE)
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
        val title = body?.get("title")?.asText()?.takeIf { it.isNotBlank() } ?: book.title
        val author = body?.get("author")?.asText()?.takeIf { it.isNotBlank() } ?: book.author
        val source = body?.get("source")?.asText()?.takeIf { it.isNotBlank() }
        val meta =
            metadataFetchService.fetchMetadata(title, author, source)
                ?: return Response(Status.NOT_FOUND).header("Content-Type", "application/json").body("""{"error":"No metadata found"}""")
        val proposal = svc.propose(userId, bookId, meta)
        return Response(Status.CREATED)
            .header("Content-Type", "application/json")
            .body(
                org.booktower.config.Json.mapper
                    .writeValueAsString(proposal),
            )
    }

    /** GET /api/books/{id}/metadata/proposals — list pending proposals for a book */
    private fun listMetadataProposals(req: Request): Response {
        val svc = metadataProposalService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val userId = AuthenticatedUser.from(req)
        val bookId =
            extractBookIdFromPath(req)
                ?: return Response(Status.BAD_REQUEST).header("Content-Type", "application/json").body("""{"error":"Invalid book ID"}""")
        val proposals = svc.listProposals(userId, bookId)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                org.booktower.config.Json.mapper
                    .writeValueAsString(proposals),
            )
    }

    /** POST /api/books/{id}/metadata/proposals/{proposalId}/apply — apply a proposal to the book */
    private fun applyMetadataProposal(req: Request): Response {
        val svc = metadataProposalService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val userId = AuthenticatedUser.from(req)
        val bookId =
            extractBookIdFromPath(req)
                ?: return Response(Status.BAD_REQUEST).header("Content-Type", "application/json").body("""{"error":"Invalid book ID"}""")
        val proposalId =
            req.uri.path
                .split("/")
                .let { parts -> parts.getOrNull(parts.indexOf("proposals") + 1) }
                ?.let { runCatching { java.util.UUID.fromString(it) }.getOrNull() }
                ?: return Response(
                    Status.BAD_REQUEST,
                ).header("Content-Type", "application/json").body("""{"error":"Invalid proposal ID"}""")
        val meta =
            svc.applyProposal(userId, bookId, proposalId)
                ?: return Response(Status.NOT_FOUND).header("Content-Type", "application/json").body("""{"error":"Proposal not found"}""")
        val updated =
            bookService.applyFetchedMetadata(userId, bookId, meta)
                ?: return Response(Status.NOT_FOUND).header("Content-Type", "application/json").body("""{"error":"Book not found"}""")
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                org.booktower.config.Json.mapper
                    .writeValueAsString(updated),
            )
    }

    /** DELETE /api/books/{id}/metadata/proposals/{proposalId} — dismiss a proposal */
    private fun dismissMetadataProposal(req: Request): Response {
        val svc = metadataProposalService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val userId = AuthenticatedUser.from(req)
        val bookId =
            extractBookIdFromPath(req)
                ?: return Response(Status.BAD_REQUEST).header("Content-Type", "application/json").body("""{"error":"Invalid book ID"}""")
        val proposalId =
            req.uri.path
                .split("/")
                .let { parts -> parts.getOrNull(parts.indexOf("proposals") + 1) }
                ?.let { runCatching { java.util.UUID.fromString(it) }.getOrNull() }
                ?: return Response(
                    Status.BAD_REQUEST,
                ).header("Content-Type", "application/json").body("""{"error":"Invalid proposal ID"}""")
        return if (svc.dismissProposal(userId, bookId, proposalId)) {
            Response(Status.NO_CONTENT)
        } else {
            Response(Status.NOT_FOUND).header("Content-Type", "application/json").body("""{"error":"Proposal not found"}""")
        }
    }

    /** GET /api/user/opds-credentials — returns whether OPDS credentials are configured */
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

    /** PUT /api/user/opds-credentials — set OPDS-specific credentials */
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

    /** DELETE /api/user/opds-credentials — remove OPDS-specific credentials */
    private fun clearOpdsCredentials(req: Request): Response {
        val svc = opdsCredentialsService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val userId = AuthenticatedUser.from(req)
        svc.clearCredentials(userId)
        return Response(Status.NO_CONTENT)
    }

    /** PUT /api/user/hardcover/key — save or clear the user's Hardcover API key */
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

    /** GET /api/user/hardcover/status — test the Hardcover connection */
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

    /** POST /api/books/{id}/hardcover/sync — manually sync a book's status to Hardcover */
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

    /** GET /api/books/{id}/hardcover/mapping — get the Hardcover book ID mapping */
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

    private fun pwaManifest(req: Request): Response {
        val manifest = """{
  "name": "BookTower",
  "short_name": "BookTower",
  "description": "Your personal digital library",
  "start_url": "/",
  "display": "standalone",
  "background_color": "#0f1117",
  "theme_color": "#6366f1",
  "icons": [
    {"src": "/static/icons/icon-192.png", "sizes": "192x192", "type": "image/png"},
    {"src": "/static/icons/icon-512.png", "sizes": "512x512", "type": "image/png"}
  ],
  "categories": ["books", "education", "utilities"]
}"""
        return Response(Status.OK)
            .header("Content-Type", "application/manifest+json")
            .body(manifest)
    }

    private fun index(req: Request): Response {
        val token = req.cookie("token")?.value
        val isAuth = token != null && jwtService.extractUserId(token) != null
        if (isAuth) {
            return pageHandler.dashboard(req)
        }
        val ctx = WebContext(req)
        val content =
            templateRenderer.render(
                "index.kte",
                mapOf<String, Any?>(
                    "title" to "BookTower",
                    "isAuthenticated" to false,
                    "username" to null,
                    "libraries" to null,
                    "showLogin" to false,
                    "showRegister" to false,
                    "registrationOpen" to registrationOpen,
                    "themeCss" to ctx.themeCss,
                    "currentTheme" to ctx.theme,
                    "lang" to ctx.lang,
                    "i18n" to ctx.i18n,
                ),
            )
        return Response(Status.OK).header("Content-Type", "text/html; charset=utf-8").body(content)
    }

    private fun loginPage(req: Request): Response {
        val ctx = WebContext(req)
        val content =
            templateRenderer.render(
                "index.kte",
                mapOf<String, Any?>(
                    "title" to "Login - BookTower",
                    "isAuthenticated" to false,
                    "username" to null,
                    "libraries" to null,
                    "showLogin" to true,
                    "showRegister" to false,
                    "registrationOpen" to registrationOpen,
                    "themeCss" to ctx.themeCss,
                    "currentTheme" to ctx.theme,
                    "lang" to ctx.lang,
                    "i18n" to ctx.i18n,
                ),
            )
        return Response(Status.OK).header("Content-Type", "text/html; charset=utf-8").body(content)
    }

    private fun registerPage(req: Request): Response {
        if (!registrationOpen) {
            return Response(Status.SEE_OTHER).header("Location", "/login")
        }
        val ctx = WebContext(req)
        val content =
            templateRenderer.render(
                "index.kte",
                mapOf<String, Any?>(
                    "title" to "Register - BookTower",
                    "isAuthenticated" to false,
                    "username" to null,
                    "libraries" to null,
                    "showLogin" to false,
                    "showRegister" to true,
                    "registrationOpen" to true,
                    "themeCss" to ctx.themeCss,
                    "currentTheme" to ctx.theme,
                    "lang" to ctx.lang,
                    "i18n" to ctx.i18n,
                ),
            )
        return Response(Status.OK).header("Content-Type", "text/html; charset=utf-8").body(content)
    }

    private fun forgotPasswordPage(req: Request): Response {
        val ctx = WebContext(req)
        val content =
            templateRenderer.render(
                "forgot-password.kte",
                mapOf<String, Any?>(
                    "themeCss" to ctx.themeCss,
                    "currentTheme" to ctx.theme,
                    "lang" to ctx.lang,
                    "i18n" to ctx.i18n,
                ),
            )
        return Response(Status.OK).header("Content-Type", "text/html; charset=utf-8").body(content)
    }

    private fun resetPasswordPage(req: Request): Response {
        val token = req.query("token") ?: ""
        val ctx = WebContext(req)
        val content =
            templateRenderer.render(
                "reset-password.kte",
                mapOf<String, Any?>(
                    "token" to token,
                    "themeCss" to ctx.themeCss,
                    "currentTheme" to ctx.theme,
                    "lang" to ctx.lang,
                    "i18n" to ctx.i18n,
                ),
            )
        return Response(Status.OK).header("Content-Type", "text/html; charset=utf-8").body(content)
    }

    private fun setTheme(req: Request): Response {
        val themeId = req.form("theme")?.trim()?.takeIf { ThemeCatalog.isValid(it) } ?: "catppuccin-mocha"
        val css = ThemeCatalog.toCssVariables(themeId)
        val themeCookie = Cookie(name = "app_theme", value = themeId, path = "/", maxAge = 365L * 24 * 3600)
        return Response(Status.OK)
            .header("Content-Type", "text/html; charset=utf-8")
            .cookie(themeCookie)
            .body("""<style id="theme-style" data-theme="$themeId">$css</style>""")
    }

    private fun setLanguage(req: Request): Response {
        val lang = req.form("lang")?.trim()?.takeIf { it in WebContext.SUPPORTED_LANGS } ?: "en"
        val langCookie = Cookie(name = "app_lang", value = lang, path = "/", maxAge = 365L * 24 * 3600)
        return Response(Status.OK)
            .cookie(langCookie)
            .header("HX-Refresh", "true")
            .body("")
    }

    // ─── Book formats / alternative files ───────────────────────────────────

    /** GET /api/books/{id}/formats */
    private fun listBookFiles(req: Request): Response {
        val svc = bookFilesService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val bookId =
            req.uri.path
                .split("/")
                .dropLast(1)
                .lastOrNull() ?: return Response(Status.BAD_REQUEST)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                org.booktower.config.Json.mapper
                    .writeValueAsString(svc.listFiles(bookId)),
            )
    }

    /** POST /api/books/{id}/formats */
    private fun addBookFile(req: Request): Response {
        val svc = bookFilesService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val bookId =
            req.uri.path
                .split("/")
                .dropLast(1)
                .lastOrNull() ?: return Response(Status.BAD_REQUEST)
        val request =
            runCatching {
                org.booktower.config.Json.mapper
                    .readValue(req.bodyString(), org.booktower.models.AddBookFileRequest::class.java)
            }.getOrNull() ?: return Response(Status.BAD_REQUEST)
                .header("Content-Type", "application/json")
                .body("""{"error":"Invalid request body"}""")

        return runCatching { svc.addFile(bookId, request) }
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

    /** DELETE /api/books/{id}/formats/{fileId} */
    private fun removeBookFile(req: Request): Response {
        val svc = bookFilesService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val parts =
            req.uri.path
                .split("/")
                .filter { it.isNotBlank() }
        val fileId = parts.lastOrNull() ?: return Response(Status.BAD_REQUEST)
        val bookId = parts.dropLast(2).lastOrNull() ?: return Response(Status.BAD_REQUEST)
        val removed = svc.removeFile(bookId, fileId)
        return if (removed) Response(Status.NO_CONTENT) else Response(Status.NOT_FOUND)
    }

    private fun comicPages(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val bookId =
            req.uri.path
                .split("/")
                .dropLast(2)
                .lastOrNull()
                ?.let { runCatching { java.util.UUID.fromString(it) }.getOrNull() }
                ?: return Response(Status.BAD_REQUEST)
        val filePath = bookService.getBookFilePath(userId, bookId) ?: return Response(Status.NOT_FOUND)
        val count = comicService.getPageCount(filePath)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body("""{"pageCount":$count}""")
    }

    /** GET /api/books/{id}/comic/{page} — returns a single comic page image */
    private fun comicPage(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val parts =
            req.uri.path
                .split("/")
                .filter { it.isNotBlank() }
        val pageIndex = parts.lastOrNull()?.toIntOrNull() ?: return Response(Status.BAD_REQUEST)
        val bookId =
            parts
                .dropLast(2)
                .lastOrNull()
                ?.let { runCatching { java.util.UUID.fromString(it) }.getOrNull() }
                ?: return Response(Status.BAD_REQUEST)
        val filePath = bookService.getBookFilePath(userId, bookId) ?: return Response(Status.NOT_FOUND)
        val bytes = comicService.getPage(filePath, pageIndex) ?: return Response(Status.NOT_FOUND)
        val pages = comicService.listPages(filePath)
        val mime = pages.getOrNull(pageIndex)?.contentType ?: "image/jpeg"
        return Response(Status.OK)
            .header("Content-Type", mime)
            .header("Cache-Control", "private, max-age=3600")
            .body(bytes.inputStream())
    }

    // ─── Demo mode ────────────────────────────────────────────────────────────

    private fun demoStatus(
        @Suppress("UNUSED_PARAMETER") req: Request,
    ): Response =
        Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                org.booktower.config.Json.mapper
                    .writeValueAsString(mapOf("demoMode" to demoMode)),
            )

    // ─── Telemetry ────────────────────────────────────────────────────────────

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

    // ─── Audiobook metadata ───────────────────────────────────────────────────

    private fun getAudiobookMeta(req: Request): Response {
        val svc = audiobookMetaService ?: return Response(Status.SERVICE_UNAVAILABLE)
        AuthenticatedUser.from(req) // ensure authenticated
        val bookId =
            req.uri.path
                .split("/")
                .dropLast(1)
                .lastOrNull() ?: return Response(Status.BAD_REQUEST)
        val meta = svc.get(bookId) ?: return Response(Status.NOT_FOUND)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                org.booktower.config.Json.mapper
                    .writeValueAsString(meta),
            )
    }

    private fun updateAudiobookMeta(req: Request): Response {
        val svc = audiobookMetaService ?: return Response(Status.SERVICE_UNAVAILABLE)
        AuthenticatedUser.from(req)
        val bookId =
            req.uri.path
                .split("/")
                .dropLast(1)
                .lastOrNull() ?: return Response(Status.BAD_REQUEST)
        val request =
            runCatching {
                org.booktower.config.Json.mapper
                    .readValue(req.bodyString(), UpdateAudiobookMetaRequest::class.java)
            }.getOrNull() ?: return Response(Status.BAD_REQUEST)
                .header("Content-Type", "application/json")
                .body("""{"error":"Invalid request body"}""")
        val result = svc.upsert(bookId, request)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                org.booktower.config.Json.mapper
                    .writeValueAsString(result),
            )
    }

    private fun deleteAudiobookMeta(req: Request): Response {
        val svc = audiobookMetaService ?: return Response(Status.SERVICE_UNAVAILABLE)
        AuthenticatedUser.from(req)
        val bookId =
            req.uri.path
                .split("/")
                .dropLast(1)
                .lastOrNull() ?: return Response(Status.BAD_REQUEST)
        return if (svc.delete(bookId)) Response(Status.NO_CONTENT) else Response(Status.NOT_FOUND)
    }

    private fun uploadAudiobookCover(req: Request): Response {
        val svc = audiobookMetaService ?: return Response(Status.SERVICE_UNAVAILABLE)
        AuthenticatedUser.from(req)
        val bookId =
            req.uri.path
                .split("/")
                .dropLast(1)
                .lastOrNull() ?: return Response(Status.BAD_REQUEST)
        val filename =
            req.header("X-Filename")?.trim()
                ?: return Response(Status.BAD_REQUEST)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"X-Filename header required"}""")
        val ext = filename.substringAfterLast('.', "").lowercase()
        if (ext !in setOf("jpg", "jpeg", "png", "webp")) {
            return Response(Status.BAD_REQUEST)
                .header("Content-Type", "application/json")
                .body("""{"error":"Unsupported image type"}""")
        }
        val bytes = req.body.stream.readBytes()
        if (bytes.isEmpty()) {
            return Response(Status.BAD_REQUEST)
                .header("Content-Type", "application/json")
                .body("""{"error":"Empty body"}""")
        }
        val coversDir = java.io.File(storageConfig.coversPath, "audiobook-covers")
        if (!coversDir.exists() && !coversDir.mkdirs()) logger.warn("Could not create covers directory: ${coversDir.absolutePath}")
        val coverFilename = "$bookId-audio.$ext"
        java.io.File(coversDir, coverFilename).writeBytes(bytes)
        svc.upsert(bookId, UpdateAudiobookMetaRequest(audioCover = "audiobook-covers/$coverFilename"))
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body("""{"coverUrl":"/api/books/$bookId/audiobook-cover"}""")
    }

    private fun getAudiobookCover(req: Request): Response {
        val svc = audiobookMetaService ?: return Response(Status.SERVICE_UNAVAILABLE)
        AuthenticatedUser.from(req)
        val bookId =
            req.uri.path
                .split("/")
                .dropLast(1)
                .lastOrNull() ?: return Response(Status.BAD_REQUEST)
        val meta = svc.get(bookId) ?: return Response(Status.NOT_FOUND)
        val coverPath = meta.audioCover ?: return Response(Status.NOT_FOUND)
        val file = java.io.File(storageConfig.coversPath, coverPath)
        if (!file.exists()) return Response(Status.NOT_FOUND)
        val contentType =
            when (file.extension.lowercase()) {
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "webp" -> "image/webp"
                else -> "application/octet-stream"
            }
        return Response(Status.OK)
            .header("Content-Type", contentType)
            .header("Content-Length", file.length().toString())
            .body(file.inputStream())
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

    /**
     * GET /api/notifications/stream — Server-Sent Events endpoint.
     * Immediately flushes all pending unread notifications as SSE events and
     * appends a `heartbeat` event, then closes the stream.  In production a
     * real client would keep the connection open; here we send the current
     * state so tests can assert on the response without blocking.
     */
    private fun streamNotifications(req: Request): Response {
        val svc = notificationService ?: return Response(Status.SERVICE_UNAVAILABLE)
        val userId = AuthenticatedUser.from(req)
        val items = svc.list(userId, unreadOnly = true)
        val sb = StringBuilder()
        for (item in items) {
            sb.append("event: notification\n")
            sb.append("data: ${org.booktower.config.Json.mapper.writeValueAsString(item)}\n\n")
        }
        sb.append("event: heartbeat\ndata: {}\n\n")
        return Response(Status.OK)
            .header("Content-Type", "text/event-stream")
            .header("Cache-Control", "no-cache")
            .body(sb.toString())
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

    private fun setBookStatus(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val bookId =
            req.uri.path
                .split("/")
                .dropLast(1)
                .lastOrNull()
                ?.let { runCatching { java.util.UUID.fromString(it) }.getOrNull() }
                ?: return Response(Status.BAD_REQUEST)
        val body =
            runCatching {
                org.booktower.config.Json.mapper
                    .readTree(req.bodyString())
            }.getOrNull()
        val statusName = body?.get("status")?.asText()?.takeIf { it.isNotBlank() && it != "NONE" }
        val status =
            statusName?.let { n ->
                org.booktower.models.ReadStatus.entries
                    .firstOrNull { it.name == n }
            }
        bookService.setStatus(userId, bookId, status)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                org.booktower.config.Json.mapper
                    .writeValueAsString(mapOf("status" to (status?.name ?: "NONE"))),
            )
    }

    // ─── Community ratings ────────────────────────────────────────────────────

    private fun getCommunityRating(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val bookId =
            req.uri.path
                .split("/")
                .dropLast(1)
                .lastOrNull()
                ?.let { runCatching { java.util.UUID.fromString(it) }.getOrNull() }
                ?: return Response(Status.BAD_REQUEST)
        val rating =
            communityRatingService?.getStored(userId, bookId)
                ?: return Response(Status.NOT_FOUND)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"Community ratings not available"}""")
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                org.booktower.config.Json.mapper
                    .writeValueAsString(rating),
            )
    }

    private fun fetchCommunityRating(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val bookId =
            req.uri.path
                .split("/")
                .dropLast(2)
                .lastOrNull()
                ?.let { runCatching { java.util.UUID.fromString(it) }.getOrNull() }
                ?: return Response(Status.BAD_REQUEST)
        val svc =
            communityRatingService
                ?: return Response(Status.SERVICE_UNAVAILABLE)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"Community rating service not configured"}""")
        val result =
            svc.fetchAndStore(userId, bookId)
                ?: return Response(Status.NOT_FOUND)
                    .header("Content-Type", "application/json")
                    .body("""{"error":"No community rating found for this book"}""")
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                org.booktower.config.Json.mapper
                    .writeValueAsString(result),
            )
    }

    // ─── Duplicate book merge ─────────────────────────────────────────────────

    private fun mergeBook(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val targetId =
            req.uri.path
                .split("/")
                .dropLast(1)
                .lastOrNull()
                ?.let { runCatching { java.util.UUID.fromString(it) }.getOrNull() }
                ?: return Response(Status.BAD_REQUEST).body("""{"error":"Invalid target book id"}""")
        val body =
            runCatching {
                org.booktower.config.Json.mapper.readValue(
                    req.bodyString(),
                    org.booktower.models.MergeBookRequest::class.java,
                )
            }.getOrNull()
                ?: return Response(Status.BAD_REQUEST).body("""{"error":"Invalid request body"}""")
        val sourceId =
            runCatching { java.util.UUID.fromString(body.sourceId) }.getOrNull()
                ?: return Response(Status.BAD_REQUEST).body("""{"error":"Invalid source book id"}""")
        if (!bookService.mergeBooks(userId, targetId, sourceId)) {
            return Response(Status.NOT_FOUND)
                .header("Content-Type", "application/json")
                .body("""{"error":"One or both books not found, or they are the same book"}""")
        }
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body("""{"merged":true}""")
    }

    // ─── Public / shareable shelves ───────────────────────────────────────────

    private fun listShelves(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val shelves = magicShelfService.getShelves(userId)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                org.booktower.config.Json.mapper
                    .writeValueAsString(shelves),
            )
    }

    private fun createShelf(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val body =
            runCatching {
                org.booktower.config.Json.mapper
                    .readTree(req.bodyString())
            }.getOrNull()
                ?: return Response(Status.BAD_REQUEST)
        val name =
            body
                .get("name")
                ?.asText()
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: return Response(Status.BAD_REQUEST)
        val ruleTypeStr =
            body.get("ruleType")?.asText()?.trim()
                ?: return Response(Status.BAD_REQUEST)
        val ruleType =
            runCatching {
                org.booktower.models.ShelfRuleType
                    .valueOf(ruleTypeStr)
            }.getOrNull()
                ?: return Response(Status.BAD_REQUEST)
        val ruleValue = body.get("ruleValue")?.takeIf { !it.isNull }?.asText()
        val shelf = magicShelfService.createShelf(userId, org.booktower.models.CreateMagicShelfRequest(name, ruleType, ruleValue))
        return Response(Status.CREATED)
            .header("Content-Type", "application/json")
            .body(
                org.booktower.config.Json.mapper
                    .writeValueAsString(shelf),
            )
    }

    private fun shareShelf(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val shelfId =
            req.uri.path
                .split("/")
                .dropLast(1)
                .lastOrNull()
                ?.let { runCatching { java.util.UUID.fromString(it) }.getOrNull() }
                ?: return Response(Status.BAD_REQUEST)
        val shelf =
            magicShelfService.shareShelf(userId, shelfId)
                ?: return Response(Status.NOT_FOUND)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                org.booktower.config.Json.mapper
                    .writeValueAsString(shelf),
            )
    }

    private fun unshareShelf(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val shelfId =
            req.uri.path
                .split("/")
                .dropLast(1)
                .lastOrNull()
                ?.let { runCatching { java.util.UUID.fromString(it) }.getOrNull() }
                ?: return Response(Status.BAD_REQUEST)
        return if (magicShelfService.unshareShelf(userId, shelfId)) {
            Response(Status.NO_CONTENT)
        } else {
            Response(Status.NOT_FOUND)
        }
    }

    private fun getPublicShelf(req: Request): Response {
        val token =
            req.uri.path
                .split("/")
                .lastOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: return Response(Status.BAD_REQUEST)
        val shelf =
            magicShelfService.getPublicShelf(token)
                ?: return Response(Status.NOT_FOUND)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                org.booktower.config.Json.mapper
                    .writeValueAsString(shelf),
            )
    }
}
