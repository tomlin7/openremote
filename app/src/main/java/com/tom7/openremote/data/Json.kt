package com.tom7.openremote.data

import kotlinx.serialization.json.Json

val AppJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = false
    isLenient = true
    coerceInputValues = true
}
