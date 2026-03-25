package org.runary.handlers

import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.runary.config.TemplateRenderer
import org.runary.services.AuthService
import org.runary.services.JwtService

class SettingsPageHandler(
    private val jwtService: JwtService,
    private val authService: AuthService,
    private val templateRenderer: TemplateRenderer,
    private val koboSyncService: org.runary.services.KoboSyncService? = null,
    private val koreaderSyncService: org.runary.services.KOReaderSyncService? = null,
    private val filterPresetService: org.runary.services.FilterPresetService? = null,
    private val scheduledTaskService: org.runary.services.ScheduledTaskService? = null,
    private val opdsCredentialsService: org.runary.services.OpdsCredentialsService? = null,
    private val contentRestrictionsService: org.runary.services.ContentRestrictionsService? = null,
    private val hardcoverSyncService: org.runary.services.HardcoverSyncService? = null,
    private val bookDeliveryService: org.runary.services.BookDeliveryService? = null,
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
