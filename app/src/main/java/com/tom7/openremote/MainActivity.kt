package com.tom7.openremote

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tom7.openremote.ui.chat.ChatScreen
import com.tom7.openremote.ui.connect.ConnectScreen
import com.tom7.openremote.ui.sessions.SessionsScreen
import com.tom7.openremote.ui.settings.SettingsScreen
import com.tom7.openremote.ui.theme.OpenRemoteTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    private val pairingLink = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pairingLink.value = pairingInput(intent)
        setContent {
            val pendingPairing by pairingLink.collectAsStateWithLifecycle()
            OpenRemoteRoot(
                pendingPairing = pendingPairing,
                onPairingConsumed = { pairingLink.value = null },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pairingInput(intent)?.let { value -> pairingLink.value = value }
    }
}

/** Pairing links arrive as https://host:port/auth/connect/<code>; the app redeems them directly. */
private fun pairingInput(intent: Intent?): String? {
    if (intent?.action != Intent.ACTION_VIEW) return null
    return intent.dataString?.takeIf { it.isNotBlank() }
}

private const val SCREEN_SESSIONS = "sessions"
private const val SCREEN_CHAT = "chat"
private const val SCREEN_SETTINGS = "settings"

@Composable
fun OpenRemoteRoot(
    pendingPairing: String? = null,
    onPairingConsumed: () -> Unit = {},
) {
    val app: AppViewModel = viewModel()
    val connection by app.connection.collectAsStateWithLifecycle()
    val themeMode by app.themeMode.collectAsStateWithLifecycle()

    OpenRemoteTheme(mode = themeMode) {
        var screen by rememberSaveable { mutableStateOf(SCREEN_SESSIONS) }
        var sessionID by rememberSaveable { mutableStateOf<String?>(null) }
        val api = app.api

        LaunchedEffect(pendingPairing) {
            val link = pendingPairing ?: return@LaunchedEffect
            onPairingConsumed()
            screen = SCREEN_SESSIONS
            sessionID = null
            app.pair(link)
        }

        BackHandler(enabled = connection != null && screen != SCREEN_SESSIONS) {
            screen = SCREEN_SESSIONS
        }

        if (connection == null || api == null) {
            ConnectScreen(app)
            return@OpenRemoteTheme
        }

        when (screen) {
            SCREEN_CHAT -> {
                val current = sessionID
                if (current == null) {
                    screen = SCREEN_SESSIONS
                } else {
                    ChatScreen(
                        api = api,
                        app = app,
                        sessionID = current,
                        onBack = {
                            screen = SCREEN_SESSIONS
                            sessionID = null
                        },
                        onOpenSession = { id ->
                            sessionID = id
                            screen = SCREEN_CHAT
                        },
                    )
                }
            }

            SCREEN_SETTINGS -> SettingsScreen(
                app = app,
                onBack = { screen = SCREEN_SESSIONS },
            )

            else -> SessionsScreen(
                api = api,
                app = app,
                onOpenSession = { session ->
                    sessionID = session.id
                    screen = SCREEN_CHAT
                },
                onOpenSettings = { screen = SCREEN_SETTINGS },
            )
        }
    }
}
