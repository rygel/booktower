package org.runary.handlers

import org.runary.config.Json
import org.runary.filters.AuthenticatedUser
import org.runary.services.KOReaderSyncService
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.routing.path

class KOReaderSyncHandler(
    private val koReaderSyncService: KOReaderSyncService,
) {
    /** POST /api/koreader/devices — register a KOReader device. */
    fun register(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val body = runCatching { Json.mapper.readTree(req.bodyString()) }.getOrNull()
        val deviceName = body?.get("deviceName")?.asText()?.takeIf { it.isNotBlank() }
        val device = koReaderSyncService.registerDevice(userId, deviceName)
        return Response(Status.CREATED)
            .header("Content-Type", "application/json")
            .body(Json.mapper.writeValueAsString(mapOf("token" to device.token, "deviceName" to device.deviceName)))
    }

    /** GET /api/koreader/devices — list registered KOReader devices. */
    fun listDevices(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val devices = koReaderSyncService.listDevices(userId)
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

    /** DELETE /api/koreader/devices/{token} */
    fun deleteDevice(req: Request): Response {
        val userId = AuthenticatedUser.from(req)
        val token = req.path("token") ?: return Response(Status.BAD_REQUEST)
        return if (koReaderSyncService.deleteDevice(userId, token)) {
            Response(Status.NO_CONTENT)
        } else {
            Response(Status.NOT_FOUND)
        }
    }

    /**
     * PUT /kobo/koreader/{token}/syncs/progress — KOReader pushes reading progress.
     * Accepts KOReader kosync format: { document, progress, percentage, device, device_id }.
     */
    fun pushProgress(req: Request): Response {
        val token = req.path("token") ?: return Response(Status.UNAUTHORIZED)
        val device = koReaderSyncService.getDevice(token) ?: return Response(Status.UNAUTHORIZED)
        val body =
            runCatching { Json.mapper.readTree(req.bodyString()) }.getOrNull()
                ?: return Response(Status.BAD_REQUEST)
        val document = body.get("document")?.asText() ?: return Response(Status.BAD_REQUEST)
        val progress = body.get("progress")?.asText()
        val percentage = body.get("percentage")?.asDouble() ?: 0.0
        val deviceName = body.get("device")?.asText()
        val deviceId = body.get("device_id")?.asText()
        koReaderSyncService.pushProgress(device.userId, document, progress ?: "", percentage, deviceName, deviceId)
        koReaderSyncService.touchLastSync(token)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(Json.mapper.writeValueAsString(mapOf("document" to document, "timestamp" to System.currentTimeMillis() / 1000)))
    }

    /**
     * GET /kobo/koreader/{token}/syncs/progress/{document} — KOReader pulls reading progress.
     */
    fun getProgress(req: Request): Response {
        val token = req.path("token") ?: return Response(Status.UNAUTHORIZED)
        val device = koReaderSyncService.getDevice(token) ?: return Response(Status.UNAUTHORIZED)
        val document = req.path("document") ?: return Response(Status.BAD_REQUEST)
        val progress =
            koReaderSyncService.getProgress(device.userId, document)
                ?: return Response(Status.NOT_FOUND)
        return Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(Json.mapper.writeValueAsString(progress))
    }
}
