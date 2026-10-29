package com.tom7.openremote.ui.connect

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.g00fy2.quickie.QRResult
import io.github.g00fy2.quickie.ScanQRCode
import com.tom7.openremote.AppViewModel

@Composable
fun ConnectScreen(app: AppViewModel) {
    val connectState by app.connectState.collectAsStateWithLifecycle()
    var url by rememberSaveable { mutableStateOf(app.lastUrl) }
    var password by rememberSaveable { mutableStateOf("") }
    var pairing by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var alternative by rememberSaveable { mutableStateOf("") }
    var permissionError by rememberSaveable { mutableStateOf<String?>(null) }
    val showPairingLink = alternative == "link"
    val showPasswordForm = alternative == "password"

    val scanLauncher = rememberLauncherForActivityResult(ScanQRCode()) { result ->
        when (result) {
            is QRResult.QRSuccess -> {
                val contents = result.content.rawValue
                if (!contents.isNullOrBlank()) {
                    permissionError = null
                    pairing = contents
                    app.pair(contents)
                }
            }
            is QRResult.QRUserCanceled -> { /* User tapped back, no action needed */ }
            is QRResult.QRMissingPermission -> {
                permissionError = "Camera permission is required to scan QR codes. Please grant camera permission in app settings."
            }
            is QRResult.QRError -> {
                permissionError = "Scan failed: ${result.exception.message ?: "Unknown error"}"
            }
        }
    }

    val busy = connectState.busy
    val pairingBusy = busy && connectState.pairing
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding(),
        ) {
            // The scroll container measures its child with an infinite height, so the content
            // states the viewport height itself to stay vertically centred while it fits.
            val viewportHeight = maxHeight
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = viewportHeight)
                        .padding(horizontal = 24.dp, vertical = 40.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Column(modifier = Modifier.fillMaxWidth().widthIn(max = 380.dp)) {
                        Brand(muted)
                        Spacer(Modifier.height(32.dp))
                        Text(
                            "Pair with your computer",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "In the OpenCode desktop app, open Settings → Pairing → Pair device. Then scan the code with this phone.",
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 20.sp),
                            color = muted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Spacer(Modifier.height(28.dp))
                        Button(
                            onClick = { scanLauncher.launch(null) },
                            enabled = !busy,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary,
                                contentColor = MaterialTheme.colorScheme.onSecondary,
                            ),
                        ) {
                            if (pairingBusy) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(17.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onSecondary,
                                )
                                Spacer(Modifier.width(9.dp))
                            } else {
                                Icon(Icons.Outlined.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(9.dp))
                            }
                            Text(
                                if (pairingBusy) "Connecting…" else "Scan QR code",
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }

                        connectState.error?.let { error ->
                            Spacer(Modifier.height(14.dp))
                            Text(
                                error,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        permissionError?.let { error ->
                            Spacer(Modifier.height(14.dp))
                            Text(
                                error,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        Spacer(Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            AltLink("Paste a link", alternative == "link", muted) {
                                alternative = if (alternative == "link") "" else "link"
                                app.clearPairingError()
                            }
                            Text(
                                "·",
                                style = MaterialTheme.typography.bodySmall,
                                color = muted.copy(alpha = 0.6f),
                                modifier = Modifier.padding(horizontal = 2.dp),
                            )
                            AltLink("Address and password", alternative == "password", muted) {
                                alternative = if (alternative == "password") "" else "password"
                                app.clearPairingError()
                            }
                        }

                        if (showPairingLink) {
                            Spacer(Modifier.height(4.dp))
                            OutlinedTextField(
                                value = pairing,
                                onValueChange = { pairing = it },
                                placeholder = { Text("Paste the pairing link") },
                                minLines = 2,
                                maxLines = 3,
                                enabled = !busy,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(10.dp))
                            FilledTonalButton(
                                onClick = { app.pair(pairing) },
                                enabled = !busy && pairing.isNotBlank(),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth().height(46.dp),
                            ) {
                                if (pairingBusy) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text("Pair", style = MaterialTheme.typography.labelLarge)
                            }
                        }

                        if (showPasswordForm) {
                            Spacer(Modifier.height(4.dp))
                            FieldLabel("Address", muted)
                            Spacer(Modifier.height(6.dp))
                            OutlinedTextField(
                                value = url,
                                onValueChange = {
                                    url = it
                                    app.clearPairingError()
                                },
                                placeholder = { Text("http://192.168.1.20:4096") },
                                singleLine = true,
                                enabled = !busy,
                                shape = RoundedCornerShape(10.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(14.dp))
                            FieldLabel("Password", muted)
                            Spacer(Modifier.height(6.dp))
                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it },
                                singleLine = true,
                                enabled = !busy,
                                shape = RoundedCornerShape(10.dp),
                                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { showPassword = !showPassword }) {
                                        Icon(
                                            if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                            contentDescription = if (showPassword) "Hide password" else "Show password",
                                            modifier = Modifier.size(18.dp),
                                            tint = muted,
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "The password printed by opencode serve.",
                                style = MaterialTheme.typography.bodySmall,
                                color = muted,
                            )
                            if (app.lastUrl.isNotBlank() && url.isBlank()) {
                                QuietLink("Use last server", muted) { url = app.lastUrl }
                            }
                            Spacer(Modifier.height(10.dp))
                            FilledTonalButton(
                                onClick = { app.connect(url, password) },
                                enabled = !busy,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth().height(46.dp),
                            ) {
                                if (busy && !connectState.pairing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text(
                                    if (busy && !connectState.pairing) "Connecting…" else "Connect",
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                        }

                        Spacer(Modifier.height(24.dp))
                        Text(
                            "Everything stays on your computer.",
                            style = MaterialTheme.typography.labelSmall,
                            color = muted.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Brand(muted: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Surface(
            shape = RoundedCornerShape(11.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.size(40.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Outlined.Terminal,
                    contentDescription = null,
                    modifier = Modifier.size(21.dp),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "OpenRemote",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.1).sp),
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            "OpenCode, from your phone",
            style = MaterialTheme.typography.bodySmall,
            color = muted,
        )
    }
}

@Composable
private fun FieldLabel(text: String, color: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
        color = color,
    )
}

@Composable
private fun QuietLink(text: String, muted: Color, onClick: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        TextButton(
            onClick = onClick,
            colors = ButtonDefaults.textButtonColors(contentColor = muted),
        ) {
            Text(text, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun AltLink(text: String, selected: Boolean, muted: Color, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        colors = ButtonDefaults.textButtonColors(
            contentColor = if (selected) MaterialTheme.colorScheme.primary else muted,
        ),
        contentPadding = PaddingValues(horizontal = 6.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}
