package com.tom7.openremote.ui.chat

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tom7.openremote.data.SessionMessage
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

data class BrowserAction(
    val toolName: String,
    val url: String?,
    val action: String?,
    val screenshotBase64: String?,
    val timestamp: Long?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserSheet(
    messages: List<SessionMessage>,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    val browserActions = remember(messages) {
        extractBrowserActions(messages)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Browser Actions",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (browserActions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No browser actions recorded",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(browserActions) { action ->
                        BrowserActionCard(action = action)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun BrowserActionCard(action: BrowserAction) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp)
    ) {
        // Header with icon and action
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.OpenInBrowser,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = getBrowserActionLabel(action.toolName),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                action.action?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // URL if present
        action.url?.let { url ->
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Language,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = url,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Screenshot if present
        action.screenshotBase64?.let { base64Data ->
            Spacer(modifier = Modifier.height(12.dp))
            val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, base64Data) {
                value = decodeBase64Image(base64Data)
            }
            bitmap?.let { bmp ->
                Image(
                    bitmap = bmp,
                    contentDescription = "Browser screenshot",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Fit
                )
            } ?: Text(
                text = "Failed to load screenshot",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

private fun decodeBase64Image(data: String): androidx.compose.ui.graphics.ImageBitmap? = runCatching {
    val bytes = Base64.decode(data, Base64.DEFAULT)
    val options = android.graphics.BitmapFactory.Options().apply {
        inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
    }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
}.getOrNull()

private fun extractBrowserActions(messages: List<SessionMessage>): List<BrowserAction> {
    val actions = mutableListOf<BrowserAction>()

    for (message in messages) {
        for (part in message.content) {
            if (part.type == "tool" && part.name?.startsWith("browser.") == true) {
                val state = part.state ?: continue
                val input = state.input as? JsonObject
                val url = input?.get("url")?.jsonPrimitive?.contentOrNull
                val action = input?.get("action")?.jsonPrimitive?.contentOrNull
                
                // Extract screenshot from content if present
                val screenshot = state.content?.firstOrNull { content ->
                    content.type == "image" && content.mime?.startsWith("image/") == true
                }?.text

                actions.add(
                    BrowserAction(
                        toolName = part.name,
                        url = url,
                        action = action,
                        screenshotBase64 = screenshot,
                        timestamp = message.time.created
                    )
                )
            }
        }
    }

    return actions
}

private fun getBrowserActionLabel(toolName: String): String {
    return when (toolName) {
        "browser.navigate" -> "Navigate"
        "browser.screenshot" -> "Screenshot"
        "browser.click" -> "Click"
        "browser.type" -> "Type"
        "browser.scroll" -> "Scroll"
        "browser.wait" -> "Wait"
        "browser.evaluate" -> "Evaluate"
        else -> toolName.removePrefix("browser.")
    }
}
