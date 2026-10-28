package com.tom7.openremote.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SettingsEthernet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tom7.openremote.AppViewModel
import com.tom7.openremote.RealtimeStatus
import com.tom7.openremote.data.ThemeMode
import com.tom7.openremote.ui.common.rememberClipboardWriter
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(app: AppViewModel, onBack: () -> Unit) {
    val connection by app.connection.collectAsStateWithLifecycle()
    val serverInfo by app.serverInfo.collectAsStateWithLifecycle()
    val location by app.location.collectAsStateWithLifecycle()
    val realtime by app.realtime.collectAsStateWithLifecycle()
    val themeMode by app.themeMode.collectAsStateWithLifecycle()
    val defaultDelivery by app.defaultDelivery.collectAsStateWithLifecycle()
    val showReasoning by app.showReasoning.collectAsStateWithLifecycle()

    val copy = rememberClipboardWriter()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var confirmDisconnect by remember { mutableStateOf(false) }
    var confirmReconnect by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Settings", style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SettingsCard(title = "Server") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(9.dp)
                            .background(
                                color = when (realtime) {
                                    RealtimeStatus.Live -> MaterialTheme.colorScheme.tertiary
                                    RealtimeStatus.Connecting -> MaterialTheme.colorScheme.primary
                                    RealtimeStatus.Offline -> MaterialTheme.colorScheme.error
                                },
                                shape = CircleShape,
                            ),
                    )
                    Spacer(Modifier.width(9.dp))
                    Text(
                        text = when (realtime) {
                            RealtimeStatus.Live -> "Connected · live updates"
                            RealtimeStatus.Connecting -> "Connecting…"
                            RealtimeStatus.Offline -> "Offline"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                InfoRow(
                    label = "Address",
                    value = connection?.url.orEmpty(),
                    onCopy = {
                        copy(connection?.url.orEmpty())
                        scope.launch { snackbar.showSnackbar("Address copied") }
                    },
                )
                serverInfo?.version?.takeIf { it.isNotBlank() }?.let {
                    InfoRow(label = "Server version", value = it)
                }
                serverInfo?.let { info ->
                    if (info.urls.isNotEmpty()) {
                        InfoRow(
                            label = "Reachable at",
                            value = info.urls.joinToString(", "),
                        )
                    }
                }
                location?.let { current ->
                    val directory = current.project.canonical.ifBlank { current.directory }
                    if (directory.isNotBlank()) {
                        InfoRow(label = "Server directory", value = directory)
                    }
                }

                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = { confirmReconnect = true },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(7.dp))
                        Text("Reconnect")
                    }
                    OutlinedButton(
                        onClick = { confirmDisconnect = true },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Outlined.SettingsEthernet, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(7.dp))
                        Text("Disconnect")
                    }
                }
            }

            SettingsCard(title = "Appearance") {
                Text(
                    "Theme",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(ThemeMode.Dark, ThemeMode.Light, ThemeMode.System).forEach { mode ->
                        FilterChip(
                            selected = themeMode == mode,
                            onClick = { app.setTheme(mode) },
                            label = {
                                Text(
                                    when (mode) {
                                        ThemeMode.System -> "System"
                                        ThemeMode.Light -> "Light"
                                        ThemeMode.Dark -> "Dark"
                                    },
                                )
                            },
                        )
                    }
                }
                Text(
                    "Dark is the default. System follows the phone's appearance setting.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SettingsCard(title = "Chat") {
                Text(
                    "Default delivery while OpenCode is working",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = defaultDelivery == "steer",
                        onClick = { app.setDefaultDelivery("steer") },
                        label = { Text("Steer") },
                    )
                    FilterChip(
                        selected = defaultDelivery == "queue",
                        onClick = { app.setDefaultDelivery("queue") },
                        label = { Text("Queue") },
                    )
                }
                Text(
                    "Steer redirects the current run at the next safe step. Queue waits until the run finishes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Show reasoning", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Display the model's thinking summaries in the timeline.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = showReasoning, onCheckedChange = { app.setShowReasoning(it) })
                }
            }

            SettingsCard(title = "Phone and desktop") {
                Text(
                    "OpenRemote talks to the same HTTP API as the OpenCode desktop app. Keep the server running on your computer and stay on the same network, or reach it through a trusted VPN.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                CodeHint("opencode serve --hostname 0.0.0.0 --port 4096")
                CodeHint("opencode pair")
                Text(
                    "Pairing creates a one-time link the app exchanges for a token. The token is stored only on this phone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                "OpenRemote · built on the OpenCode API",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }

    if (confirmReconnect) {
        AlertDialog(
            onDismissRequest = { confirmReconnect = false },
            title = { Text("Reconnect?") },
            text = { Text("The app will reconnect to the current server address and restart live updates.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmReconnect = false
                        app.retry()
                        scope.launch { snackbar.showSnackbar("Reconnecting…") }
                    },
                ) { Text("Reconnect") }
            },
            dismissButton = { TextButton(onClick = { confirmReconnect = false }) { Text("Cancel") } },
        )
    }

    if (confirmDisconnect) {
        AlertDialog(
            onDismissRequest = { confirmDisconnect = false },
            title = { Text("Disconnect?") },
            text = { Text("The token will be removed from this phone. The address is kept so you can reconnect quickly. Sessions stay on the computer.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDisconnect = false
                        app.disconnect()
                        onBack()
                    },
                ) { Text("Disconnect", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDisconnect = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            content()
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, onCopy: (() -> Unit)? = null) {
    if (value.isBlank()) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                value,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (onCopy != null) {
            IconButton(onClick = onCopy, modifier = Modifier.size(34.dp)) {
                Icon(
                    Icons.Outlined.ContentCopy,
                    contentDescription = "Copy",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CodeHint(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
        )
    }
}
