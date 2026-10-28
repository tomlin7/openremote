package com.tom7.openremote.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl

data class PairingLink(val urls: List<String>, val code: String)

private val CODE = Regex("^[A-Za-z0-9_-]+$")
private val CONNECT_PATH = Regex("^/auth/connect/([^/]+)$")

/**
 * Accepts the three shapes OpenCode emits:
 *  - a pairing link: https://host:4096/auth/connect/<code>
 *  - a scanner payload: {"code":"...","urls":["http://host:4096"]}
 *  - a bare code, paired with the server URL currently in the form
 */
fun parsePairingInput(input: String, fallbackUrl: String? = null): PairingLink? {
    val value = input.trim()
    if (value.isEmpty()) return null

    if (value.startsWith("{")) {
        val payload = runCatching { AppJson.parseToJsonElement(value).jsonObject }.getOrNull() ?: return null
        val code = payload["code"]?.jsonPrimitive?.contentOrNull ?: return null
        if (!CODE.matches(code)) return null
        val urls = (payload["urls"] as? JsonArray)?.mapNotNull { element ->
            (element as? JsonPrimitive)?.contentOrNull?.let { normalizeServerUrl(it) }
        }.orEmpty().distinct()
        if (urls.isEmpty()) return null
        return PairingLink(urls, code)
    }

    if (value.contains("://")) {
        val url = runCatching { value.toHttpUrl() }.getOrNull() ?: return null
        val code = CONNECT_PATH.find(url.encodedPath)?.groupValues?.getOrNull(1) ?: return null
        if (!CODE.matches(code)) return null
        val origin = url.newBuilder()
            .encodedPath("/")
            .query(null)
            .fragment(null)
            .build()
            .toString()
            .trimEnd('/')
        return PairingLink(listOf(origin), code)
    }

    if (CODE.matches(value)) {
        val fallback = fallbackUrl?.let { normalizeServerUrl(it) } ?: return null
        return PairingLink(listOf(fallback), value)
    }

    return null
}

fun pairingPayloadFromLink(link: PairingLink): String = JsonObject(
    mapOf(
        "code" to JsonPrimitive(link.code),
        "urls" to JsonArray(link.urls.map { JsonPrimitive(it) }),
    ),
).toString()
