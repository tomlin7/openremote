package com.tom7.openremote.ui.connect

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.tom7.openremote.AppViewModel
import com.tom7.openremote.data.userMessage

@Composable
fun ConnectScreen(app: AppViewModel) {
    val connectState by app.connectState.collectAsStateWithLifecycle()
    var url by rememberSaveable { mutableStateOf(app.lastUrl) }
    var password by rememberSaveable { mutableStateOf("") }
    var pairing by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var showHelp by rememberSaveable { mutableStateOf(false) }

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val contents = result.contents
        if (!contents.isNullOrBlank()) {
            pairing = contents
            app.pair(contents)
        }
    }
    val scanOptions = remember {
        ScanOptions()
            .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            .setPrompt("Scan the pairing QR code")
            .setBeepEnabled(false)
            .setOrientationLocked(false)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.background,
                    ),
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding()
                .padding(horizontal = 22.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                    modifier = Modifier.size(52.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Outlined.Terminal,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("OpenRemote", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "OpenCode, from your phone",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "Connect to the OpenCode server running on your computer. Everything stays on your machine.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Server", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = url,
                        onValueChange = {
                            url = it
                            app.clearPairingError()
                        },
                        label = { Text("Address") },
                        placeholder = { Text("http://192.168.1.20:4096") },
                        singleLine = true,
                        enabled = !connectState.busy,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        singleLine = true,
                        enabled = !connectState.busy,
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    contentDescription = if (showPassword) "Hide password" else "Show password",
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (app.lastUrl.isNotBlank() && url.isBlank()) {
                        TextButton(onClick = { url = app.lastUrl }) { Text("Use last server") }
                    }
                    FilledTonalButton(
                        onClick = { app.connect(url, password) },
                        enabled = !connectState.busy,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        if (connectState.busy && !connectState.pairing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Spacer(Modifier.width(10.dp))
                        }
                        Text(if (connectState.busy && !connectState.pairing) "Connecting" else "Connect")
                    }
                }
            }

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.Link,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Pair instantly", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        "Run `opencode pair` on your computer, then paste the link or scan the QR code. The link carries a one-time code.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = pairing,
                        onValueChange = { pairing = it },
                        label = { Text("Pairing link or code") },
                        minLines = 1,
                        maxLines = 3,
                        enabled = !connectState.busy,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { scanLauncher.launch(scanOptions) },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Outlined.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Scan")
                        }
                        FilledTonalButton(
                            onClick = { app.pair(pairing) },
                            enabled = !connectState.busy && pairing.isNotBlank(),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            if (connectState.busy && connectState.pairing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text("Pair")
                        }
                    }
                }
            }

            connectState.error?.let { error ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(14.dp),
                    )
                }
            }

            TextButton(onClick = { showHelp = !showHelp }, modifier = Modifier.align(Alignment.Start)) {
                Text(if (showHelp) "Hide setup help" else "How do I connect?")
            }
            if (showHelp) {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        HelpStep("1", "Start the server so your phone can reach it:")
                        CodeLine("opencode serve --hostname 0.0.0.0 --port 4096")
                        HelpStep("2", "Connect your phone to the same Wi-Fi network.")
                        HelpStep("3", "Enter this computer's LAN address, for example 192.168.1.20, and the server password.")
                        HelpStep("Or", "Run `opencode pair` on the computer and scan the QR code.")
                        Text(
                            "OpenRemote talks to the same HTTP API as the OpenCode desktop app. Use HTTPS or a trusted network for remote access.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Text(
                "OpenRemote · built on the OpenCode API",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun HelpStep(number: String, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Surface(
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
            shape = CircleShape,
            modifier = Modifier.size(22.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    number,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun CodeLine(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}
