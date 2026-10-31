package com.tom7.openremote

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tom7.openremote.ui.chat.ChatScreen
import com.tom7.openremote.ui.connect.ConnectScreen
import com.tom7.openremote.ui.inbox.InboxScreen
import com.tom7.openremote.ui.sessions.SessionsScreen
import com.tom7.openremote.ui.settings.SettingsScreen
import com.tom7.openremote.ui.theme.OpenRemoteTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    private val pairingLink = MutableStateFlow<String?>(null)
    
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* no-op - we just want to show notifications if granted */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Request notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        
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

private const val SCREEN_HOME = "home"
private const val SCREEN_SESSIONS = "sessions"
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
        var screen by rememberSaveable { mutableStateOf(SCREEN_HOME) }
        var sessionID by rememberSaveable { mutableStateOf<String?>(null) }
        val api = app.api

        LaunchedEffect(pendingPairing) {
            val link = pendingPairing ?: return@LaunchedEffect
            onPairingConsumed()
            screen = SCREEN_SESSIONS
            sessionID = null
            app.pair(link)
        }

        BackHandler(enabled = connection != null && screen != SCREEN_SESSIONS && sessionID != null) {
            if (sessionID != null) {
                sessionID = null
                screen = SCREEN_SESSIONS
            } else {
                screen = SCREEN_SESSIONS
            }
        }

        if (connection == null || api == null) {
            ConnectScreen(app)
            return@OpenRemoteTheme
        }

        // Chat screen doesn't use bottom nav
        if (sessionID != null) {
            ChatScreen(
                api = api,
                app = app,
                sessionID = sessionID!!,
                onBack = {
                    sessionID = null
                },
                onOpenSession = { id ->
                    sessionID = id
                },
            )
            return@OpenRemoteTheme
        }

        // Main screen with bottom navigation
        Scaffold(
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        icon = {
                            Icon(
                                if (screen == SCREEN_HOME) Icons.Filled.Inbox else Icons.Outlined.Inbox,
                                contentDescription = null,
                            )
                        },
                        label = { Text("Inbox") },
                        selected = screen == SCREEN_HOME,
                        onClick = { screen = SCREEN_HOME },
                    )
                    NavigationBarItem(
                        icon = {
                            Icon(
                                if (screen == SCREEN_SESSIONS) Icons.Filled.List else Icons.Outlined.List,
                                contentDescription = null,
                            )
                        },
                        label = { Text("Sessions") },
                        selected = screen == SCREEN_SESSIONS,
                        onClick = { screen = SCREEN_SESSIONS },
                    )
                    NavigationBarItem(
                        icon = {
                            Icon(
                                if (screen == SCREEN_SETTINGS) Icons.Filled.Settings else Icons.Outlined.Settings,
                                contentDescription = null,
                            )
                        },
                        label = { Text("Settings") },
                        selected = screen == SCREEN_SETTINGS,
                        onClick = { screen = SCREEN_SETTINGS },
                    )
                }
            },
        ) { padding ->
            when (screen) {
                SCREEN_HOME -> InboxScreen(
                    api = api,
                    app = app,
                    onOpenSession = { session ->
                        sessionID = session.id
                    },
                    onNewSession = { screen = SCREEN_SESSIONS },
                )

                SCREEN_SETTINGS -> SettingsScreen(
                    app = app,
                    onBack = { screen = SCREEN_SESSIONS },
                    modifier = Modifier.padding(padding),
                )

                else -> SessionsScreen(
                    api = api,
                    app = app,
                    onOpenSession = { session ->
                        sessionID = session.id
                    },
                    onOpenSettings = { screen = SCREEN_SETTINGS },
                    modifier = Modifier.padding(padding),
                )
            }
        }
    }
}
