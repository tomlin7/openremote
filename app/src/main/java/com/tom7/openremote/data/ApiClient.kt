package com.tom7.openremote.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.net.URLEncoder
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.serialization.json.jsonObject

@Serializable
private data class CreateSessionBody(
    val location: LocationRef? = null,
    val title: String? = null,
    val agent: String? = null,
    val model: ModelRef? = null,
)

@Serializable
private data class UpdateSessionBody(
    val title: String? = null,
    val archived: Boolean? = null,
)

@Serializable
private data class PromptBody(
    val text: String,
    val files: List<PromptFile>? = null,
    val agents: List<PromptAgent>? = null,
    val skills: List<PromptSkill>? = null,
    val delivery: String? = null,
    val metadata: JsonObject? = null,
    val resume: Boolean? = null,
)

@Serializable
private data class CommandBody(
    val name: String,
    val text: String,
    val files: List<PromptFile>? = null,
    val delivery: String? = null,
)

@Serializable
private data class AgentBody(val agent: String)

@Serializable
private data class ModelBody(val model: ModelRef)

@Serializable
private data class PermissionReplyBody(val decision: String, val message: String? = null)

@Serializable
private data class InboxDeliveryBody(val delivery: String)

@Serializable
private data class ViewBody(val idle: Long)

@Serializable
private data class ShellBody(val command: String)

@Serializable
private data class ForkBody(val before: String? = null)

@Serializable
private data class ActiveSessionsResponse(val data: JsonObject = JsonObject(emptyMap()))

private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

class OpenCodeApi(
    val baseUrl: String,
    password: String,
    val client: OkHttpClient,
) {
    val authorization: String = Credentials.basic("opencode", password)

    // -----------------------------------------------------------------------
    // Server
    // -----------------------------------------------------------------------

    suspend fun info(): ServerInfo = get("/api/info")

    suspend fun location(): LocationInfo = get("/api/location")

    suspend fun projects(): List<ProjectInfo> = get("/api/project")

    suspend fun activeSessions(): Set<String> {
        val response: ActiveSessionsResponse = get("/api/session/active")
        return response.data.keys
    }

    // -----------------------------------------------------------------------
    // Sessions
    // -----------------------------------------------------------------------

    suspend fun sessions(
        directory: String? = null,
        search: String? = null,
        archived: Boolean? = null,
        limit: Int = 100,
        cursor: String? = null,
    ): SessionPage {
        val query = buildList {
            add("limit" to limit.toString())
            add("order" to "desc")
            if (!directory.isNullOrBlank()) add("directory" to directory)
            if (!search.isNullOrBlank()) add("search" to search)
            if (archived != null) add("archived" to archived.toString())
            if (!cursor.isNullOrBlank()) add("cursor" to cursor)
        }
        return get("/api/session", query)
    }

    suspend fun session(id: String): SessionInfo = get<SessionResponse>("/api/session/${path(id)}").data

    suspend fun createSession(
        directory: String,
        title: String? = null,
        agent: String? = null,
        model: ModelRef? = null,
    ): SessionInfo {
        val body = CreateSessionBody(
            location = directory.takeIf { it.isNotBlank() }?.let { LocationRef(it) },
            title = title?.takeIf { it.isNotBlank() },
            agent = agent,
            model = model,
        )
        return post<SessionResponse>("/api/session", body).data
    }

    suspend fun renameSession(id: String, title: String) {
        patch("/api/session/${path(id)}", UpdateSessionBody(title = title))
    }

    suspend fun archiveSession(id: String, archived: Boolean) {
        patch("/api/session/${path(id)}", UpdateSessionBody(archived = archived))
    }

    suspend fun deleteSession(id: String) {
        call("DELETE", "/api/session/${path(id)}")
    }

    suspend fun forkSession(id: String, before: String? = null): SessionInfo =
        post<SessionResponse>("/api/session/${path(id)}/fork", ForkBody(before)).data

    suspend fun interrupt(id: String): Boolean {
        val result: JsonObject = post("/api/session/${path(id)}/interrupt?resume=true", UnitBody)
        return result["interrupted"]?.toString()?.toBooleanStrictOrNull() ?: false
    }

    suspend fun compact(id: String) {
        post<JsonObject>("/api/session/${path(id)}/compact", UnitBody)
    }

    suspend fun runShell(id: String, command: String) {
        call("POST", "/api/session/${path(id)}/shell", body = ShellBody(command))
    }

    suspend fun markViewed(id: String, idle: Long) {
        call("POST", "/api/session/${path(id)}/view", body = ViewBody(idle))
    }

    suspend fun switchAgent(id: String, agent: String) {
        call("POST", "/api/session/${path(id)}/agent", body = AgentBody(agent))
    }

    suspend fun switchModel(id: String, model: ModelRef) {
        call("POST", "/api/session/${path(id)}/model", body = ModelBody(model))
    }

    // -----------------------------------------------------------------------
    // Messages
    // -----------------------------------------------------------------------

    suspend fun messages(
        sessionID: String,
        limit: Int = 200,
        order: String = "desc",
        cursor: String? = null,
    ): MessagePage {
        val query = buildList {
            add("limit" to limit.toString())
            if (cursor.isNullOrBlank()) add("order" to order)
            if (!cursor.isNullOrBlank()) add("cursor" to cursor)
        }
        return get("/api/session/${path(sessionID)}/message", query)
    }

    suspend fun sendPrompt(
        sessionID: String,
        text: String,
        files: List<PromptFile> = emptyList(),
        agents: List<PromptAgent> = emptyList(),
        delivery: String? = null,
    ) {
        post<JsonObject>(
            "/api/session/${path(sessionID)}/prompt",
            PromptBody(
                text = text,
                files = files.takeIf { it.isNotEmpty() },
                agents = agents.takeIf { it.isNotEmpty() },
                delivery = delivery,
            ),
        )
    }

    suspend fun runCommand(
        sessionID: String,
        name: String,
        text: String,
        files: List<PromptFile> = emptyList(),
        delivery: String? = null,
    ) {
        call(
            "POST",
            "/api/session/${path(sessionID)}/command",
            body = CommandBody(
                name = name,
                text = text,
                files = files.takeIf { it.isNotEmpty() },
                delivery = delivery,
            ),
        )
    }

    suspend fun inbox(sessionID: String): List<InboxItem> =
        get<InboxResponse>("/api/session/${path(sessionID)}/inbox").data

    suspend fun steerInbox(sessionID: String, inboxID: String) {
        patch("/api/session/${path(sessionID)}/inbox/${path(inboxID)}", InboxDeliveryBody("steer"))
    }

    suspend fun cancelInbox(sessionID: String, inboxID: String) {
        call("DELETE", "/api/session/${path(sessionID)}/inbox/${path(inboxID)}")
    }

    // -----------------------------------------------------------------------
    // Catalog
    // -----------------------------------------------------------------------

    suspend fun models(): List<ModelInfo> = get<LocationWrappedModels>("/api/model").data

    suspend fun providers(): List<ProviderInfo> = get<LocationWrappedProviders>("/api/provider").data

    suspend fun agents(): List<AgentInfo> = get<LocationWrappedAgents>("/api/agent").data

    suspend fun commands(): List<CommandInfo> = get<LocationWrappedCommands>("/api/command").data

    // -----------------------------------------------------------------------
    // Permissions
    // -----------------------------------------------------------------------

    suspend fun permissions(sessionID: String): List<PermissionRequest> =
        get<PermissionList>("/api/session/${path(sessionID)}/permission").data

    suspend fun replyPermission(sessionID: String, requestID: String, decision: String, message: String? = null) {
        call(
            "POST",
            "/api/session/${path(sessionID)}/permission/${path(requestID)}/reply",
            body = PermissionReplyBody(decision, message),
        )
    }

    // -----------------------------------------------------------------------
    // Forms
    // -----------------------------------------------------------------------

    suspend fun forms(sessionID: String): List<FormInfo> =
        get<FormList>("/api/session/${path(sessionID)}/form").data

    suspend fun replyForm(sessionID: String, formID: String, answer: Map<String, JsonElement>) {
        call(
            "POST",
            "/api/session/${path(sessionID)}/form/${path(formID)}/reply",
            body = FormReply(answer),
        )
    }

    suspend fun cancelForm(sessionID: String, formID: String, message: String? = null) {
        val query = if (message.isNullOrBlank()) emptyList() else listOf("message" to message)
        call("DELETE", "/api/session/${path(sessionID)}/form/${path(formID)}", query = query)
    }

    // -----------------------------------------------------------------------
    // Transport
    // -----------------------------------------------------------------------

    fun url(path: String, query: List<Pair<String, String>> = emptyList()): HttpUrl {
        val builder = (baseUrl + path).toHttpUrl().newBuilder()
        query.forEach { (key, value) -> builder.addQueryParameter(key, value) }
        return builder.build()
    }

    private suspend inline fun <reified T> get(path: String, query: List<Pair<String, String>> = emptyList()): T =
        decode(call("GET", path, query = query))

    private suspend inline fun <reified T> post(path: String, body: Any): T =
        decode(call("POST", path, body = body))

    private suspend fun patch(path: String, body: Any) {
        call("PATCH", path, body = body)
    }

    private inline fun <reified T> decode(body: String): T {
        if (body.isBlank()) {
            throw ApiException(500, "The server returned an empty response.")
        }
        return AppJson.decodeFromString<T>(body)
    }

    private suspend fun call(
        method: String,
        path: String,
        query: List<Pair<String, String>> = emptyList(),
        body: Any? = null,
    ): String {
        val requestBody = when {
            body == null -> null
            body is String -> body.toRequestBody(JSON_MEDIA)
            body === UnitBody -> "{}".toRequestBody(JSON_MEDIA)
            else -> AppJson.encodeToString(serializerFor(body), body).toRequestBody(JSON_MEDIA)
        }
        val request = Request.Builder()
            .url(url(path, query))
            .header("Authorization", authorization)
            .header("Accept", "application/json")
            .method(method, requestBody)
            .build()
        return execute(request)
    }

    private suspend fun execute(request: Request): String {
        val response = client.newCall(request).await()
        return withContext(Dispatchers.IO) {
            response.use {
                val body = it.body?.string().orEmpty()
                if (!it.isSuccessful) {
                    throw ApiException(it.code, apiErrorSummary(it.code, body), body)
                }
                body
            }
        }
    }

    private fun path(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}

private object UnitBody

@Suppress("UNCHECKED_CAST")
private fun <T : Any> serializerFor(value: T): kotlinx.serialization.KSerializer<T> {
    val serializer = when (value) {
        is CreateSessionBody -> CreateSessionBody.serializer()
        is UpdateSessionBody -> UpdateSessionBody.serializer()
        is PromptBody -> PromptBody.serializer()
        is CommandBody -> CommandBody.serializer()
        is AgentBody -> AgentBody.serializer()
        is ModelBody -> ModelBody.serializer()
        is PermissionReplyBody -> PermissionReplyBody.serializer()
        is InboxDeliveryBody -> InboxDeliveryBody.serializer()
        is ViewBody -> ViewBody.serializer()
        is ShellBody -> ShellBody.serializer()
        is ForkBody -> ForkBody.serializer()
        is FormReply -> FormReply.serializer()
        else -> error("Unsupported request body: ${value::class.java.name}")
    }
    return serializer as kotlinx.serialization.KSerializer<T>
}

private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (continuation.isActive) continuation.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            if (continuation.isActive) continuation.resume(response) else response.close()
        }
    })
}

fun normalizeServerUrl(input: String): String? {
    var value = input.trim()
    if (value.isEmpty()) return null
    if (!value.contains("://")) value = "http://$value"
    val url = runCatching { value.toHttpUrl() }.getOrNull() ?: return null
    if (url.scheme != "http" && url.scheme != "https") return null
    if (url.username.isNotEmpty() || url.password.isNotEmpty()) return null
    if (url.query != null || url.fragment != null) return null
    return url.toString().trimEnd('/')
}

fun Throwable.userMessage(): String = when (this) {
    is ApiException -> message
    is java.net.UnknownHostException -> "Could not find the server. Check the address and Wi-Fi."
    is java.net.ConnectException -> "Could not connect. Is the server running and reachable?"
    is java.net.SocketTimeoutException -> "The server took too long to answer."
    else -> message ?: this::class.java.simpleName
}
