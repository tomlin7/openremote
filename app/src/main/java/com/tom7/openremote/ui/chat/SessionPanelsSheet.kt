package com.tom7.openremote.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Web
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tom7.openremote.data.SessionMessage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionPanelsSheet(
    messages: List<SessionMessage>,
    api: com.tom7.openremote.data.OpenCodeApi? = null,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    // Parse session activity from messages
    val sessionActivity = remember(messages) {
        parseSessionActivity(messages)
    }

    var selectedPanel by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        if (selectedPanel == "files" && api != null) {
            FileBrowserSheet(
                api = api,
                onDismiss = { selectedPanel = null },
            )
            return@ModalBottomSheet
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                "Session Activity",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            // Files Panel
            PanelCard(
                icon = Icons.Outlined.Folder,
                title = "Files",
                subtitle = "Browse project files",
                onClick = { selectedPanel = "files" },
            )
            Spacer(Modifier.height(8.dp))

            // Files Changed Panel
            if (sessionActivity.filesChanged.isNotEmpty()) {
                PanelCard(
                    icon = Icons.Outlined.Description,
                    title = "Files Changed",
                    subtitle = "${sessionActivity.filesChanged.size} files",
                    onClick = { /* TODO: Show file details */ },
                )
                Spacer(Modifier.height(8.dp))
            }

            // Tool Calls Panel
            if (sessionActivity.toolCalls > 0) {
                PanelCard(
                    icon = Icons.Outlined.Code,
                    title = "Tool Calls",
                    subtitle = "${sessionActivity.toolCalls} tools used",
                    onClick = { /* TODO: Show tool details */ },
                )
                Spacer(Modifier.height(8.dp))
            }

            // Terminal Panel
            if (sessionActivity.terminalCalls > 0) {
                PanelCard(
                    icon = Icons.Outlined.Terminal,
                    title = "Terminal",
                    subtitle = "${sessionActivity.terminalCalls} commands",
                    onClick = { /* TODO: Show terminal output */ },
                )
                Spacer(Modifier.height(8.dp))
            }

            // Browser Panel
            if (sessionActivity.browserCalls > 0) {
                PanelCard(
                    icon = Icons.Outlined.Web,
                    title = "Browser",
                    subtitle = "${sessionActivity.browserCalls} actions",
                    onClick = { /* TODO: Show browser screenshots */ },
                )
                Spacer(Modifier.height(8.dp))
            }

            // Empty state
            if (sessionActivity.filesChanged.isEmpty() && 
                sessionActivity.toolCalls == 0 &&
                sessionActivity.terminalCalls == 0 &&
                sessionActivity.browserCalls == 0) {
                Text(
                    "No activity yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 32.dp),
                )
            }
        }
    }
}

@Composable
private fun PanelCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

data class SessionActivity(
    val filesChanged: List<String>,
    val toolCalls: Int,
    val terminalCalls: Int,
    val browserCalls: Int,
)

private fun parseSessionActivity(messages: List<SessionMessage>): SessionActivity {
    val filesChanged = mutableSetOf<String>()
    var toolCalls = 0
    var terminalCalls = 0
    var browserCalls = 0

    messages.forEach { message ->
        if (message.type == "assistant") {
            message.content.forEach { part ->
                if (part.name != null) {
                    toolCalls++
                    val toolName = part.name.lowercase()
                    when {
                        toolName.contains("terminal") || toolName.contains("shell") -> terminalCalls++
                        toolName.contains("browser") -> browserCalls++
                        toolName.contains("write") || toolName.contains("edit") || toolName.contains("create") -> {
                            // Extract file path from tool input
                            part.state?.input?.toString()?.let { input ->
                                val pathMatch = Regex("\"path\"\\s*:\\s*\"([^\"]+)\"").find(input)
                                pathMatch?.groupValues?.get(1)?.let { filesChanged.add(it) }
                            }
                        }
                    }
                }
            }
        }
    }

    return SessionActivity(
        filesChanged = filesChanged.toList(),
        toolCalls = toolCalls,
        terminalCalls = terminalCalls,
        browserCalls = browserCalls,
    )
}
