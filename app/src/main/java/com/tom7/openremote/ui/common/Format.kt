package com.tom7.openremote.ui.common

import com.tom7.openremote.data.SessionInfo
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun relativeTime(millis: Long): String {
    if (millis <= 0) return ""
    val now = System.currentTimeMillis()
    val delta = now - millis
    if (delta < 45_000) return "now"
    if (delta < 3_600_000) return "${delta / 60_000}m"
    if (delta < 86_400_000) return "${delta / 3_600_000}h"
    if (delta < 172_800_000) return "yesterday"
    if (delta < 604_800_000) return "${delta / 86_400_000}d"
    return SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(millis))
}

fun clockTime(millis: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))

fun formatCost(cost: Double): String = when {
    cost <= 0.0 -> ""
    cost < 0.01 -> "<$0.01"
    else -> String.format(Locale.US, "$%.2f", cost)
}

fun formatTokens(count: Long?): String = when {
    count == null || count <= 0 -> ""
    count >= 1_000_000 -> String.format(Locale.US, "%.1fM", count / 1_000_000.0)
    count >= 1_000 -> String.format(Locale.US, "%.0fK", count / 1_000.0)
    else -> count.toString()
}

fun sessionSubtitle(session: SessionInfo): String {
    val parts = buildList {
        session.agent?.takeIf { it.isNotBlank() }?.let { add(it) }
        session.model?.id?.takeIf { it.isNotBlank() }?.let { add(it) }
        if (session.time.updated > 0) add(relativeTime(session.time.updated))
    }
    return parts.joinToString(" · ")
}

fun prettyJson(element: JsonElement?): String {
    if (element == null || element is JsonNull) return ""
    return runCatching {
        PrettyJson.encodeToString(JsonElement.serializer(), element)
    }.getOrDefault(element.toString())
}

private val PrettyJson = Json {
    prettyPrint = true
    explicitNulls = false
}

fun JsonElement?.asPrettyString(): String {
    val value = this ?: return ""
    return when (value) {
        is JsonPrimitive -> value.content
        is JsonObject -> prettyJson(value)
        else -> prettyJson(value)
    }
}

fun toolDisplayName(name: String): String {
    val cleaned = name.substringAfterLast('.').substringAfterLast('/')
    return cleaned.replace('_', ' ').replace('-', ' ').replaceFirstChar { it.uppercase() }
}
