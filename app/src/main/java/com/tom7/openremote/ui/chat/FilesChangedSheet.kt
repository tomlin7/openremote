package com.tom7.openremote.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tom7.openremote.data.SessionMessage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesChangedSheet(
    messages: List<SessionMessage>,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedFile by remember { mutableStateOf<String?>(null) }

    val filesChanged = remember(messages) {
        extractFilesChanged(messages)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            TopAppBar(
                title = {
                    Text(
                        text = "Files Changed",
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )

            if (filesChanged.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "No files changed yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = 32.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(filesChanged, key = { it.path }) { fileChange ->
                        FileChangeRow(
                            fileChange = fileChange,
                            onClick = { selectedFile = fileChange.path },
                        )
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

data class FileChange(
    val path: String,
    val changeType: ChangeType,
)

enum class ChangeType {
    ADDED, MODIFIED, DELETED
}

private fun extractFilesChanged(messages: List<SessionMessage>): List<FileChange> {
    val filesMap = mutableMapOf<String, ChangeType>()

    messages.forEach { message ->
        if (message.type == "assistant") {
            message.content.forEach { part ->
                if (part.type == "tool" && part.name != null) {
                    val toolName = part.name.lowercase()
                    val input = part.state?.input?.toString() ?: ""

                    // Extract path from tool input
                    val pathMatch = Regex("\"path\"\\s*:\\s*\"([^\"]+)\"").find(input)
                    val path = pathMatch?.groupValues?.get(1) ?: return@forEach

                    when {
                        toolName.contains("write") || toolName.contains("create") -> {
                            filesMap[path] = ChangeType.ADDED
                        }
                        toolName.contains("edit") || toolName.contains("modify") -> {
                            filesMap[path] = ChangeType.MODIFIED
                        }
                        toolName.contains("delete") || toolName.contains("remove") -> {
                            filesMap[path] = ChangeType.DELETED
                        }
                    }
                }
            }
        }
    }

    return filesMap.map { (path, changeType) ->
        FileChange(path = path, changeType = changeType)
    }.sortedBy { it.path }
}

@Composable
private fun FileChangeRow(
    fileChange: FileChange,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = when (fileChange.changeType) {
                    ChangeType.ADDED -> Icons.Outlined.Add
                    ChangeType.MODIFIED -> Icons.Outlined.Edit
                    ChangeType.DELETED -> Icons.Outlined.Delete
                },
                contentDescription = null,
                tint = when (fileChange.changeType) {
                    ChangeType.ADDED -> MaterialTheme.colorScheme.primary
                    ChangeType.MODIFIED -> MaterialTheme.colorScheme.tertiary
                    ChangeType.DELETED -> MaterialTheme.colorScheme.error
                },
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = fileChange.path.substringAfterLast("/"),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = fileChange.path,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
