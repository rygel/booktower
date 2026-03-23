package org.booktower.handlers

import org.booktower.config.TemplateRenderer
import org.booktower.services.AuthService
import org.booktower.services.JwtService
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status

class SettingsPageHandler(
    private val jwtService: JwtService,
    private val authService: AuthService,
    private val templateRenderer: TemplateRenderer,
    private val koboSyncService: org.booktower.services.KoboSyncService? = null,
    private val koreaderSyncService: org.booktower.services.KOReaderSyncService? = null,
    private val filterPresetService: org.booktower.services.FilterPresetService? = null,
    private val scheduledTaskService: org.booktower.services.ScheduledTaskService? = null,
    private val opdsCredentialsService: org.booktower.services.OpdsCredentialsService? = null,
    private val contentRestrictionsService: org.booktower.services.ContentRestrictionsService? = null,
    private val hardcoverSyncService: org.booktower.services.HardcoverSyncService? = null,
    private val bookDeliveryService: org.booktower.services.BookDeliveryService? = null,
) {
    private fun auth(req: Request) = pageAuth(req, jwtService, authService)

    private fun pc(req: Request) = pageContext(req, jwtService, authService)

    fun devices(req: Request): Response {
        val userId = auth(req) ?: return redirectToLogin()
        val pc = pc(req)
        val koboDevices = koboSyncService?.listDevices(userId) ?: emptyList()
        val koreaderDevices = koreaderSyncService?.listDevices(userId) ?: emptyList()
        return htmlOk(templateRenderer.render("devices.kte", pc.toMap("koboDevices" to koboDevices, "koreaderDevices" to koreaderDevices)))
    }

    fun filterPresets(req: Request): Response {
        val userId = auth(req) ?: return redirectToLogin()
        val pc = pc(req)
        val presets = filterPresetService?.list(userId) ?: emptyList()
        return htmlOk(templateRenderer.render("filter-presets.kte", pc.toMap("presets" to presets)))
    }

    fun scheduledTasks(req: Request): Response {
        val userId = auth(req) ?: return redirectToLogin()
        if (!pageAuthIsAdmin(req, jwtService)) return Response(Status.FORBIDDEN)
        val pc = pc(req)
        val tasks = scheduledTaskService?.list() ?: emptyList()
        return htmlOk(templateRenderer.render("scheduled-tasks.kte", pc.toMap("tasks" to tasks)))
    }

    fun opdsSettings(req: Request): Response {
        val userId = auth(req) ?: return redirectToLogin()
        val pc = pc(req)
        val creds = opdsCredentialsService?.getCredentials(userId)
        return htmlOk(
            templateRenderer.render(
                "opds-settings.kte",
                pc.toMap("hasCredentials" to (creds != null), "opdsUsername" to (creds?.opdsUsername ?: "")),
            ),
        )
    }

    fun contentRestrictions(req: Request): Response {
        val userId = auth(req) ?: return redirectToLogin()
        val pc = pc(req)
        val restrictions = contentRestrictionsService?.get(userId)
        return htmlOk(
            templateRenderer.render(
                "content-restrictions.kte",
                pc.toMap("maxAgeRating" to (restrictions?.maxAgeRating ?: ""), "blockedTags" to (restrictions?.blockedTags?.joinToString(", ") ?: "")),
            ),
        )
    }

    fun hardcoverSettings(req: Request): Response {
        val userId = auth(req) ?: return redirectToLogin()
        val pc = pc(req)
        val hasKey = hardcoverSyncService?.hasApiKey(userId) ?: false
        return htmlOk(templateRenderer.render("hardcover-settings.kte", pc.toMap("hasApiKey" to hasKey)))
    }

    fun bookDelivery(req: Request): Response {
        val userId = auth(req) ?: return redirectToLogin()
        val pc = pc(req)
        val recipients = bookDeliveryService?.listRecipients(userId) ?: emptyList()
        return htmlOk(templateRenderer.render("book-delivery.kte", pc.toMap("recipients" to recipients)))
    }
}
