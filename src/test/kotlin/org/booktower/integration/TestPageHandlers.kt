package org.booktower.integration

import org.booktower.config.TemplateRenderer
import org.booktower.handlers.BrowsePageHandler
import org.booktower.handlers.DiscoveryPageHandler
import org.booktower.handlers.PageHandler
import org.booktower.handlers.SettingsPageHandler
import org.booktower.handlers.StatsPageHandler
import org.booktower.services.*

/**
 * Shared factory for constructing all page handlers in tests.
 * Centralizes handler construction so adding a new service param only requires one change.
 */
data class TestPageHandlers(
    val pageHandler: PageHandler,
    val browsePageHandler: BrowsePageHandler,
    val statsPageHandler: StatsPageHandler,
    val settingsPageHandler: SettingsPageHandler,
    val discoveryPageHandler: DiscoveryPageHandler,
) {
    companion object {
        fun create(
            jwtService: JwtService,
            authService: AuthService,
            libraryService: LibraryService,
            bookService: BookService,
            bookmarkService: BookmarkService,
            userSettingsService: UserSettingsService,
            analyticsService: AnalyticsService,
            annotationService: AnnotationService,
            metadataFetchService: MetadataFetchService,
            magicShelfService: MagicShelfService,
            templateRenderer: TemplateRenderer,
            readingSessionService: ReadingSessionService? = null,
            libraryWatchService: LibraryWatchService? = null,
            bookLinkService: BookLinkService? = null,
            bookSharingService: BookSharingService? = null,
            backgroundTaskService: BackgroundTaskService? = null,
            webhookService: WebhookService? = null,
            libraryStatsService: LibraryStatsService? = null,
            readingTimelineService: ReadingTimelineService? = null,
            readingSpeedService: ReadingSpeedService? = null,
            libraryHealthService: LibraryHealthService? = null,
            discoveryService: DiscoveryService? = null,
            readingListService: ReadingListService? = null,
            wishlistService: WishlistService? = null,
            collectionService: CollectionService? = null,
            koboSyncService: KoboSyncService? = null,
            koreaderSyncService: KOReaderSyncService? = null,
            filterPresetService: FilterPresetService? = null,
            scheduledTaskService: ScheduledTaskService? = null,
            opdsCredentialsService: OpdsCredentialsService? = null,
            contentRestrictionsService: ContentRestrictionsService? = null,
            hardcoverSyncService: HardcoverSyncService? = null,
            bookDeliveryService: BookDeliveryService? = null,
            bookDropService: BookDropService? = null,
            metadataProposalService: MetadataProposalService? = null,
        ): TestPageHandlers {
            val pageHandler =
                PageHandler(
                    jwtService, authService, libraryService, bookService, bookmarkService,
                    userSettingsService, analyticsService, annotationService, metadataFetchService,
                    magicShelfService, templateRenderer, readingSessionService, libraryWatchService,
                    bookLinkService, bookSharingService, backgroundTaskService,
                )
            val browsePageHandler =
                BrowsePageHandler(jwtService, authService, bookService, magicShelfService, templateRenderer)
            val statsPageHandler =
                StatsPageHandler(
                    jwtService, authService, analyticsService, templateRenderer,
                    readingSessionService, libraryStatsService, readingTimelineService,
                    readingSpeedService, libraryHealthService,
                )
            val settingsPageHandler =
                SettingsPageHandler(
                    jwtService, authService, templateRenderer,
                    koboSyncService, koreaderSyncService, filterPresetService,
                    scheduledTaskService, opdsCredentialsService, contentRestrictionsService,
                    hardcoverSyncService, bookDeliveryService,
                )
            val discoveryPageHandler =
                DiscoveryPageHandler(
                    jwtService, authService, libraryService, bookService, templateRenderer,
                    discoveryService, readingListService, wishlistService, collectionService,
                    webhookService, bookDropService, metadataProposalService,
                )
            return TestPageHandlers(pageHandler, browsePageHandler, statsPageHandler, settingsPageHandler, discoveryPageHandler)
        }
    }
}
