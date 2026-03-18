package org.booktower.routers

import org.booktower.filters.AuthenticatedUser
import org.booktower.services.AuthorMetadataService
import org.booktower.services.BookService
import org.booktower.services.MetadataFetchService
import org.booktower.services.MetadataLockService
import org.booktower.services.MetadataProposalService
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.then
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind

class MetadataApiRouter(
    private val filters: FilterSet,
    private val metadataFetchService: MetadataFetchService,
    private val bookService: BookService,
    private val metadataProposalService: MetadataProposalService?,
    private val metadataLockService: MetadataLockService?,
    private val authorMetadataService: AuthorMetadataService?,
) {
    fun routes(): List<RoutingHttpHandler> =
        listOf(
            "/api/metadata/search" bind Method.GET to filters.auth.then(::metadataSearch),
            "/api/metadata/sources" bind Method.GET to filters.auth.then(::metadataSources),
            // Metadata proposal/review workflow
            "/api/books/{id}/metadata/propose" bind Method.POST to filters.auth.then(::proposeMetadata),
            "/api/books/{id}/metadata/proposals" bind Method.GET to filters.auth.then(::listMetadataProposals),
            "/api/books/{id}/metadata/proposals/{proposalId}/apply" bind Method.POST to filters.auth.then(::applyMetadataProposal),
            "/api/books/{id}/metadata/proposals/{proposalId}" bind Method.DELETE to filters.auth.then(::dismissMetadataProposal),
            "/api/authors/{name}/metadata" bind Method.GET to filters.auth.then(::authorMetadata),
            // Metadata locks
            "/api/books/{id}/metadata-locks" bind Method.GET to filters.auth.then(::getMetadataLocks),
            "/api/books/{id}/metadata-locks" bind Method.PUT to filters.auth.then(::setMetadataLocks),
        )

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

    private fun metadataSources(req: Request): Response =
        Response(Status.OK)
            .header("Content-Type", "application/json")
            .body(
                org.booktower.config.Json.mapper.writeValueAsString(
                    mapOf("sources" to org.booktower.services.METADATA_SOURCES),
                ),
            )

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
}
