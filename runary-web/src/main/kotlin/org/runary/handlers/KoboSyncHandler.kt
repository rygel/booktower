package org.runary.handlers

import org.runary.config.Json
import org.runary.filters.AuthenticatedUser
import org.runary.services.KoboSyncService
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.routing.path
import java.util.UUID

class KoboSyncHandler(
    private val koboSyncService: KoboSyncService,
) {
    /** POST /api/kobo/devices — register a Kobo device, returns the device token. */
    fun register(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val body = runCatching { Json.mapper.readTree(req.bodyString()) }.getOrNull()
        val deviceName = body?.get("deviceName")?.asText()?.takeIf { it.isNotBlank() }
        val device = koboSyncService.registerDevice(userId, deviceName)
        return Response(Status.CREATED)
            .header("Content-Type", "application/json")
            .body(Json.mapper.writeValueAsString(mapOf("token" to device.token, "deviceName" to device.deviceName)))
    }

    /** GET /api/kobo/devices — list registered Kobo devices. */
    fun listDevices(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val devices = koboSyncService.listDevices(userId)
        val body =
            devices.map {
                mapOf(
                    "token" to it.token,
                    "deviceName" to it.deviceName,
                    "lastSyncAt" to it.lastSyncAt?.toString(),
                    "createdAt" to it.createdAt.toString(),
                )
            }
        return Response(Status.OK).header("Content-Type", "application/json").body(Json.mapper.writeValueAsString(body))
    }

    /** DELETE /api/kobo/devices/{token} — remove a registered device. */
    fun deleteDevice(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val token = req.path("token") ?: return Response(Status.BAD_REQUEST)
        return if (koboSyncService.deleteDevice(userId, token)) {
            Response(Status.NO_CONTENT)
        } else {
            Response(Status.NOT_FOUND)
        }
    }

    /** GET /kobo/{token}/v1/initialization — Kobo calls this first to discover settings. */
    fun initialization(req: Request): Response {
        val token = req.path("token") ?: return Response(Status.UNAUTHORIZED)
        val device = koboSyncService.getDevice(token) ?: return Response(Status.UNAUTHORIZED)
        val body =
            mapOf(
                "Resources" to
                    mapOf(
                        "image_host" to "",
                        "image_url_quality_template" to "",
                        "image_url_template" to "",
                    ),
                "BookmarkDate" to "1970-01-01T00:00:00Z",
                "DeviceId" to token,
                "DisplayProfile" to "default",
            )
        return Response(Status.OK).header("Content-Type", "application/json").body(Json.mapper.writeValueAsString(body))
    }

    /** POST /kobo/{token}/v1/library/sync — Kobo polls this to get the library. Supports delta sync via X-Kobo-Sync-Token. */
    fun sync(req: Request): Response {
        val token = req.path("token") ?: return Response(Status.UNAUTHORIZED)
        val device = koboSyncService.getDevice(token) ?: return Response(Status.UNAUTHORIZED)
        val sinceToken = req.header("X-Kobo-Sync-Token")?.takeIf { it.isNotBlank() }
        val books = koboSyncService.buildDeltaBookList(device.userId, sinceToken)
        val newSyncToken = System.currentTimeMillis().toString()
        koboSyncService.touchLastSync(token)
        val body =
            mapOf(
                "BookEntitlements" to books,
                "Continues" to false,
                "SyncToken" to newSyncToken,
            )
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .header("X-Kobo-Sync-Token", newSyncToken)
            .body(Json.mapper.writeValueAsString(body))
    }

    /** GET /kobo/{token}/v1/library/snapshot — full library snapshot for initial sync. */
    fun snapshot(req: Request): Response {
        val token = req.path("token") ?: return Response(Status.UNAUTHORIZED)
        val device = koboSyncService.getDevice(token) ?: return Response(Status.UNAUTHORIZED)
        val snapshot = koboSyncService.buildSnapshot(device.userId)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(Json.mapper.writeValueAsString(snapshot))
    }

    /** PUT /kobo/{token}/v1/library/{bookId}/reading-state — Kobo pushes reading progress with CFI location support. */
    fun readingState(req: Request): Response {
        val token = req.path("token") ?: return Response(Status.UNAUTHORIZED)
        val device = koboSyncService.getDevice(token) ?: return Response(Status.UNAUTHORIZED)
        val bookId =
            req.path("bookId")?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                ?: return Response(Status.BAD_REQUEST)
        val body = runCatching { Json.mapper.readTree(req.bodyString()) }.getOrNull()
        val bookmark = body?.get("CurrentBookmark")
        val pct = bookmark?.get("ContentSourceProgressPercent")?.asDouble()
        val page = bookmark?.get("Location")?.asText()?.toIntOrNull()
        val location = bookmark?.get("Location")?.asText()?.takeIf { it.isNotBlank() && !it.all { c -> c.isDigit() } }
        val locationType = bookmark?.get("LocationType")?.asText()?.takeIf { it.isNotBlank() }
        koboSyncService.updateReadingState(device.userId, bookId, pct, page, location, locationType)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(Json.mapper.writeValueAsString(mapOf("RequestResult" to "RequestAccepted")))
    }
}
