package com.tom7.openremote.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// ---------------------------------------------------------------------------
// Session
// ---------------------------------------------------------------------------

@Serializable
data class ModelRef(
    val id: String,
    val providerID: String,
    val variant: String? = null,
) {
    val label: String get() = id
}

@Serializable
data class LocationRef(val directory: String = "", val workspaceID: String? = null)

@Serializable
data class SessionTime(
    val created: Long = 0,
    val updated: Long = 0,
    val idle: Long? = null,
    val viewed: Long? = null,
    val archived: Long? = null,
)

@Serializable
data class SessionInfo(
    val id: String,
    val parentID: String? = null,
    val projectID: String = "",
    val agent: String? = null,
    val model: ModelRef? = null,
    val cost: Double = 0.0,
    val outcome: String? = null,
    val time: SessionTime = SessionTime(),
    val title: String? = null,
    val location: LocationRef? = null,
    val subpath: String? = null,
    val metadata: JsonObject? = null,
) {
    val directory: String get() = location?.directory.orEmpty()
    val isArchived: Boolean get() = time.archived != null
    val displayTitle: String get() = title?.takeIf { it.isNotBlank() } ?: "Untitled session"
}

@Serializable
data class PageCursor(val previous: String? = null, val next: String? = null)

@Serializable
data class SessionPage(val data: List<SessionInfo> = emptyList(), val cursor: PageCursor = PageCursor())

@Serializable
data class SessionResponse(val data: SessionInfo)

@Serializable
data class ProjectInfo(
    val id: String,
    val canonical: String,
    val vcs: String? = null,
    val name: String? = null,
    val time: JsonObject? = null,
    val sandboxes: List<String> = emptyList(),
) {
    val displayName: String
        get() = name?.takeIf { it.isNotBlank() }
            ?: canonical.trimEnd('\\', '/').substringAfterLast('\\').substringAfterLast('/').ifBlank { canonical }
}

@Serializable
data class ProjectCurrent(val id: String = "", val directory: String = "", val canonical: String = "")

@Serializable
data class LocationInfo(val directory: String = "", val project: ProjectCurrent = ProjectCurrent())

@Serializable
data class ServerPaths(val tmp: String = "")

@Serializable
data class ServerCapabilities(val persistentPty: Boolean = false)

@Serializable
data class ServerInfo(
    val version: String = "",
    val pid: Int = 0,
    val urls: List<String> = emptyList(),
    val paths: ServerPaths? = null,
    val capabilities: ServerCapabilities? = null,
)

// ---------------------------------------------------------------------------
// File System
// ---------------------------------------------------------------------------

@Serializable
data class FileSystemEntry(
    val path: String,
    val type: String, // "file" or "directory"
)

@Serializable
data class FileSystemListResponse(
    val data: List<FileSystemEntry> = emptyList(),
)

// ---------------------------------------------------------------------------
// Messages
// ---------------------------------------------------------------------------

@Serializable
data class MessageTime(val created: Long = 0, val completed: Long? = null)

@Serializable
data class PromptFile(
    val uri: String,
    val name: String? = null,
    val description: String? = null,
)

@Serializable
data class PromptAgent(val name: String, val mention: JsonObject? = null)

@Serializable
data class PromptSkill(val id: String, val name: String, val text: String? = null)

@Serializable
data class ApiError(
    val type: String = "",
    val message: String = "",
    val status: Int? = null,
) {
    val summary: String get() = message.ifBlank { type.ifBlank { "Unknown error" } }
}

@Serializable
data class RetryInfo(val attempt: Int = 0, val at: Long = 0, val error: ApiError? = null)

@Serializable
data class ToolContent(
    val type: String = "text",
    val text: String? = null,
    val uri: String? = null,
    val mime: String? = null,
    val name: String? = null,
)

@Serializable
data class ToolState(
    val status: String = "running",
    val input: JsonElement? = null,
    val content: List<ToolContent>? = null,
    val metadata: JsonObject? = null,
    val error: ApiError? = null,
)

@Serializable
data class AssistantPart(
    val type: String,
    val text: String? = null,
    val id: String? = null,
    val name: String? = null,
    val executed: Boolean? = null,
    val state: ToolState? = null,
    val time: MessageTime? = null,
)

@Serializable
data class ShellOutput(
    val output: String = "",
    val cursor: Int = 0,
    val size: Int = 0,
    val truncated: Boolean = false,
)

@Serializable
data class SessionMessage(
    val id: String,
    val type: String,
    val time: MessageTime = MessageTime(),
    val metadata: JsonObject? = null,
    val text: String? = null,
    val files: List<PromptFile> = emptyList(),
    val agents: List<PromptAgent> = emptyList(),
    val skills: List<PromptSkill> = emptyList(),
    val agent: String? = null,
    val model: ModelRef? = null,
    val content: List<AssistantPart> = emptyList(),
    val finish: String? = null,
    val error: ApiError? = null,
    val retry: RetryInfo? = null,
    val cost: Double? = null,
    val outcome: String? = null,
    val previous: JsonElement? = null,
    val location: LocationRef? = null,
    val projectID: String? = null,
    val subpath: String? = null,
    val skill: String? = null,
    val name: String? = null,
    val description: String? = null,
    // Shell
    val shellID: String? = null,
    val command: String? = null,
    val status: String? = null,
    val exit: Double? = null,
    val output: ShellOutput? = null,
    // Compaction
    val reason: String? = null,
    val summary: String? = null,
    // Inbox
    val delivery: String? = null,
) {
    val displayText: String get() = metadata?.get("displayText")?.jsonPrimitive?.contentOrNull ?: text.orEmpty()
    val isUser: Boolean get() = type == "user"
    val isAssistant: Boolean get() = type == "assistant"
    val isEmptyAssistant: Boolean get() = isAssistant && content.isEmpty() && error == null
}

@Serializable
data class MessagePage(val data: List<SessionMessage> = emptyList(), val cursor: PageCursor = PageCursor())

@Serializable
data class InboxPayload(
    val text: String = "",
    val files: List<PromptFile> = emptyList(),
    val agents: List<PromptAgent> = emptyList(),
    val skills: List<PromptSkill> = emptyList(),
    val metadata: JsonObject? = null,
)

@Serializable
data class InboxItem(
    val id: String,
    val sessionID: String = "",
    val time: MessageTime = MessageTime(),
    val type: String = "user",
    val payload: InboxPayload = InboxPayload(),
    val delivery: String? = null,
) {
    fun toMessage(): SessionMessage = SessionMessage(
        id = id,
        type = type,
        time = time,
        metadata = payload.metadata,
        text = payload.text,
        files = payload.files,
        agents = payload.agents,
        skills = payload.skills,
        delivery = delivery,
    )
}

@Serializable
data class InboxResponse(val data: List<InboxItem> = emptyList())

// ---------------------------------------------------------------------------
// Catalog
// ---------------------------------------------------------------------------

@Serializable
data class ModelVariant(val id: String, val settings: JsonObject? = null)

@Serializable
data class ModelLimit(val context: Long? = null, val output: Long? = null)

@Serializable
data class ModelCapabilities(val input: List<String> = emptyList(), val tools: Boolean = false)

@Serializable
data class ModelCost(val input: Double = 0.0, val output: Double = 0.0)

@Serializable
data class ModelInfo(
    val id: String,
    val modelID: String = id,
    val providerID: String = "",
    val family: String? = null,
    val name: String = "",
    val variants: List<ModelVariant> = emptyList(),
    val cost: JsonElement? = null,
    val status: String = "active",
    val enabled: Boolean = true,
    val limit: ModelLimit? = null,
    val capabilities: ModelCapabilities? = null,
) {
    val displayName: String get() = name.ifBlank { modelID }
    fun ref(variant: String? = null) = ModelRef(id = modelID, providerID = providerID, variant = variant)
    val supportsImages: Boolean get() = capabilities?.input?.contains("image") == true
}

@Serializable
data class LocationWrappedModels(val location: LocationRef? = null, val data: List<ModelInfo> = emptyList())

@Serializable
data class ProviderInfo(
    val id: String,
    val name: String = id,
    val activation: String = "auto",
)

@Serializable
data class LocationWrappedProviders(val data: List<ProviderInfo> = emptyList())

@Serializable
data class AgentInfo(
    val id: String,
    val name: String = id,
    val model: ModelRef? = null,
    val description: String? = null,
    val mode: String = "primary",
    val hidden: Boolean = false,
    val color: String? = null,
    val steps: Int? = null,
)

@Serializable
data class LocationWrappedAgents(val data: List<AgentInfo> = emptyList())

@Serializable
data class CommandInfo(val name: String, val description: String? = null)

@Serializable
data class LocationWrappedCommands(val data: List<CommandInfo> = emptyList())

// ---------------------------------------------------------------------------
// Permission
// ---------------------------------------------------------------------------

@Serializable
data class PermissionRequest(
    val id: String,
    val sessionID: String = "",
    val action: String = "",
    val resources: List<String> = emptyList(),
    val save: List<String> = emptyList(),
    val metadata: JsonObject? = null,
    val message: String? = null,
    val source: JsonObject? = null,
)

@Serializable
data class PermissionList(val data: List<PermissionRequest> = emptyList())

// ---------------------------------------------------------------------------
// Forms
// ---------------------------------------------------------------------------

@Serializable
data class FormOption(val value: String, val label: String, val description: String? = null)

@Serializable
data class FormCondition(
    val key: String,
    val op: String,
    val value: JsonElement,
)

@Serializable
data class FormField(
    val key: String,
    val type: String,
    val title: String? = null,
    val description: String? = null,
    val required: Boolean? = null,
    val hidden: Boolean? = null,
    @SerialName("when") val conditions: List<FormCondition>? = null,
    val format: String? = null,
    val minLength: Int? = null,
    val maxLength: Int? = null,
    val pattern: String? = null,
    val placeholder: String? = null,
    val default: JsonElement? = null,
    val options: List<FormOption> = emptyList(),
    val custom: Boolean? = null,
    val minimum: Double? = null,
    val maximum: Double? = null,
    val minItems: Int? = null,
    val maxItems: Int? = null,
    val url: String? = null,
) {
    val label: String get() = title?.takeIf { it.isNotBlank() } ?: key
}

@Serializable
data class FormInfo(
    val id: String,
    val sessionID: String = "",
    val title: String = "",
    val fields: List<FormField> = emptyList(),
    val metadata: JsonObject? = null,
)

@Serializable
data class FormList(val data: List<FormInfo> = emptyList())

@Serializable
data class FormReply(val answer: Map<String, JsonElement>)

// ---------------------------------------------------------------------------
// Events
// ---------------------------------------------------------------------------

@Serializable
data class ServerEvent(
    val id: String = "",
    val created: Long = 0,
    val type: String = "",
    val data: JsonObject = JsonObject(emptyMap()),
    val location: JsonObject? = null,
) {
    val sessionID: String?
        get() {
            data["sessionID"]?.jsonPrimitive?.contentOrNull?.let { return it }
            (data["form"] as? JsonObject)?.get("sessionID")?.jsonPrimitive?.contentOrNull?.let { return it }
            return null
        }

    fun string(key: String): String? = data[key]?.jsonPrimitive?.contentOrNull
    fun obj(key: String): JsonObject? = data[key] as? JsonObject
    fun array(key: String): JsonArray? = data[key] as? JsonArray
    fun boolean(key: String): Boolean? = data[key]?.jsonPrimitive?.booleanOrNull
}

@Serializable
data class SessionStatus(
    val type: String = "idle",
    val attempt: Int? = null,
    val message: String? = null,
    val next: Long? = null,
) {
    val busy: Boolean get() = type == "busy" || type == "retry"
}

// ---------------------------------------------------------------------------
// Errors
// ---------------------------------------------------------------------------

class ApiException(
    val statusCode: Int,
    override val message: String,
    val body: String? = null,
) : Exception(message)

fun apiErrorSummary(statusCode: Int, body: String): String {
    val trimmed = body.trim()
    if (trimmed.startsWith("{")) {
        runCatching {
            val obj = AppJson.parseToJsonElement(trimmed).jsonObject
            obj["message"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let { return it }
            obj["error"]?.let { error ->
                if (error is JsonObject) {
                    error["message"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let { return it }
                }
                (error as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }?.let { return it }
            }
        }
    }
    return when (statusCode) {
        401, 403 -> "Authentication failed ($statusCode). Check the server password."
        404 -> "Not found (404)."
        else -> if (trimmed.isNotBlank() && trimmed.length < 300) trimmed else "Request failed ($statusCode)"
    }
}
