package org.runary.routers

import org.runary.handlers.OidcHandler
import org.http4k.core.Method
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind

class OidcRouter(
    private val oidcHandler: OidcHandler?,
) {
    fun routes(): List<RoutingHttpHandler> =
        listOf(
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
        )
}
