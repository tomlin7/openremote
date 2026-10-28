package com.tom7.openremote.data

import android.content.Context

enum class ThemeMode { System, Light, Dark }

data class SavedConnection(val url: String, val password: String)

class ConnectionStore(context: Context) {
    private val prefs = context.getSharedPreferences("openremote", Context.MODE_PRIVATE)

    fun loadConnection(): SavedConnection? {
        val url = prefs.getString("url", null)?.takeIf { it.isNotBlank() } ?: return null
        return SavedConnection(url, prefs.getString("password", "").orEmpty())
    }

    fun lastUrl(): String? = prefs.getString("url", null)?.takeIf { it.isNotBlank() }

    fun saveConnection(connection: SavedConnection) {
        prefs.edit().putString("url", connection.url).putString("password", connection.password).apply()
    }

    fun clearConnection() {
        prefs.edit().remove("password").apply()
    }

    var themeMode: ThemeMode
        get() = runCatching { ThemeMode.valueOf(prefs.getString("theme", null).orEmpty()) }
            .getOrDefault(ThemeMode.Dark)
        set(value) {
            prefs.edit().putString("theme", value.name).apply()
        }

    var defaultDelivery: String
        get() = prefs.getString("delivery", "steer")?.takeIf { it == "queue" } ?: "steer"
        set(value) {
            prefs.edit().putString("delivery", if (value == "queue") "queue" else "steer").apply()
        }

    var showReasoning: Boolean
        get() = prefs.getBoolean("reasoning", true)
        set(value) {
            prefs.edit().putBoolean("reasoning", value).apply()
        }
}
