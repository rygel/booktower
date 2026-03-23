package org.runary.handlers

import org.runary.config.TemplateRenderer
import org.runary.services.AnalyticsService
import org.runary.services.AuthService
import org.runary.services.JwtService
import org.runary.services.ReadingSessionService
import org.http4k.core.Request
import org.http4k.core.Response

class StatsPageHandler(
    private val jwtService: JwtService,
    private val authService: AuthService,
    private val analyticsService: AnalyticsService,
    private val templateRenderer: TemplateRenderer,
    private val readingSessionService: ReadingSessionService? = null,
    private val libraryStatsService: org.runary.services.LibraryStatsService? = null,
    private val readingTimelineService: org.runary.services.ReadingTimelineService? = null,
    private val readingSpeedService: org.runary.services.ReadingSpeedService? = null,
    private val libraryHealthService: org.runary.services.LibraryHealthService? = null,
) {
    private fun auth(req: Request) = pageAuth(req, jwtService, authService)

    private fun pc(req: Request) = pageContext(req, jwtService, authService)

    fun analytics(req: Request): Response {
        val userId = auth(req) ?: return redirectToLogin()
        val pc = pc(req)
        val summary = analyticsService.getSummary(userId)
        val recentSessions = readingSessionService?.getRecentSessions(userId, 20) ?: emptyList()
        return htmlOk(templateRenderer.render("analytics.kte", pc.toMap("summary" to summary, "recentSessions" to recentSessions)))
    }

    fun libraryStats(req: Request): Response {
        val userId = auth(req) ?: return redirectToLogin()
        val pc = pc(req)
        val stats = libraryStatsService?.getStats(userId)
        return htmlOk(
            templateRenderer.render(
                "librarystats.kte",
                pc.toMap("stats" to stats),
            ),
        )
    }

    fun timeline(req: Request): Response {
        val userId = auth(req) ?: return redirectToLogin()
        val pc = pc(req)
        val days = 90
        val entries = readingTimelineService?.getTimeline(userId, days) ?: emptyList()
        return htmlOk(templateRenderer.render("timeline.kte", pc.toMap("entries" to entries, "days" to days)))
    }

    fun readingSpeed(req: Request): Response {
        val userId = auth(req) ?: return redirectToLogin()
        val pc = pc(req)
        val stats = readingSpeedService?.getStats(userId)
        return htmlOk(
            templateRenderer.render(
                "reading-speed.kte",
                pc.toMap(
                    "avgPagesPerHour" to (stats?.averagePagesPerHour ?: 0.0),
                    "totalReadingMinutes" to (stats?.totalReadingMinutes ?: 0L),
                    "currentBookEstimate" to stats?.currentBookEstimate,
                    "recentSessions" to (stats?.recentSessions ?: emptyList<Any>()),
                ),
            ),
        )
    }

    fun libraryHealth(req: Request): Response {
        val userId = auth(req) ?: return redirectToLogin()
        val pc = pc(req)
        val summary = libraryHealthService?.check(userId)
        return htmlOk(
            templateRenderer.render(
                "library-health.kte",
                pc.toMap(
                    "totalIssues" to (summary?.totalIssues ?: 0),
                    "libraries" to (summary?.libraries ?: emptyList<Any>()),
                ),
            ),
        )
    }
}
