package com.tom7.openremote.ui.chat

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.tom7.openremote.data.AgentInfo
import com.tom7.openremote.data.CommandInfo
import com.tom7.openremote.data.ModelInfo
import com.tom7.openremote.data.ModelRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAX_ATTACHMENT_BYTES = 10 * 1024 * 1024

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatComposer(
    draft: String,
    onDraftChange: (String) -> Unit,
    attachments: List<OutgoingAttachment>,
    onAttachmentsChange: (List<OutgoingAttachment>) -> Unit,
    busy: Boolean,
    sending: Boolean,
    delivery: String,
    onDeliveryChange: (String) -> Unit,
    model: ModelRef?,
    models: List<ModelInfo>,
    agent: String?,
    agents: List<AgentInfo>,
    commands: List<CommandInfo>,
    onPickModel: () -> Unit,
    onPickAgent: () -> Unit,
    onPickCommand: () -> Unit,
    onSend: (String, List<OutgoingAttachment>, String?) -> Unit,
    onStop: () -> Unit,
    onError: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching { readAttachment(context, uri) }
                .onSuccess { attachment ->
                    if (attachments.size >= 6) {
                        onError("You can attach up to 6 files.")
                        return@onSuccess
                    }
                    onAttachmentsChange(attachments + attachment)
                }
                .onFailure { error ->
                    onError(error.message ?: "Could not read that file.")
                }
        }
    }

    val modelName = remember(models, model) {
        models.firstOrNull { it.modelID == model?.id && it.providerID == model.providerID }?.name
            ?: model?.id
            ?: "Model"
    }
    val agentName = remember(agents, agent) {
        agents.firstOrNull { it.id == agent }?.name ?: agent ?: "Agent"
    }
    val commandQuery = draft.substringBefore(' ').removePrefix("/")
    val suggestions = remember(commands, draft, busy) {
        if (!draft.startsWith("/") || busy) emptyList()
        else commands.filter {
            it.name.startsWith(commandQuery, ignoreCase = true) &&
                (commandQuery.isEmpty() || draft.substringAfter(' ', "").isEmpty())
        }.take(4)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        if (suggestions.isNotEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            ) {
                Column {
                    suggestions.forEach { command ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onDraftChange("/${command.name} ") }
                                .padding(horizontal = 14.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "/${command.name}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = command.description.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                if (attachments.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        attachments.forEach { attachment ->
                            AttachmentChip(
                                attachment = attachment,
                                onRemove = { onAttachmentsChange(attachments - attachment) },
                            )
                        }
                    }
                }
                BasicTextField(
                    value = draft,
                    onValueChange = onDraftChange,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    maxLines = 8,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp, max = 180.dp)
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    decorationBox = { inner ->
                        if (draft.isEmpty()) {
                            Text(
                                text = "Message OpenCode…",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        inner()
                    },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { filePicker.launch(arrayOf("*/*")) }, modifier = Modifier.size(38.dp)) {
                        Icon(
                            Icons.Outlined.AttachFile,
                            contentDescription = "Attach file",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    IconButton(onClick = onPickCommand, modifier = Modifier.size(38.dp)) {
                        Icon(
                            Icons.Outlined.Bolt,
                            contentDescription = "Slash commands",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    AssistChip(
                        onClick = onPickModel,
                        label = {
                            Text(
                                modelName,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                        shape = RoundedCornerShape(9.dp),
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                        border = null,
                    )
                    Spacer(Modifier.width(6.dp))
                    AssistChip(
                        onClick = onPickAgent,
                        label = {
                            Text(
                                agentName,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                        shape = RoundedCornerShape(9.dp),
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                        border = null,
                    )
                    Spacer(Modifier.weight(1f))
                    if (busy) {
                        var deliveryMenu by remember { mutableStateOf(false) }
                        Box {
                            AssistChip(
                                onClick = { deliveryMenu = true },
                                leadingIcon = {
                                    Icon(
                                        Icons.Outlined.SwapVert,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                    )
                                },
                                label = {
                                    Text(
                                        if (delivery == "queue") "Queue" else "Steer",
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                },
                                shape = RoundedCornerShape(9.dp),
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                    labelColor = MaterialTheme.colorScheme.primary,
                                ),
                                border = null,
                            )
                            DropdownMenu(expanded = deliveryMenu, onDismissRequest = { deliveryMenu = false }) {
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text("Steer")
                                            Text(
                                                "Redirect this run at the next step",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    },
                                    onClick = {
                                        onDeliveryChange("steer")
                                        deliveryMenu = false
                                    },
                                )
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text("Queue")
                                            Text(
                                                "Send after this run finishes",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    },
                                    onClick = {
                                        onDeliveryChange("queue")
                                        deliveryMenu = false
                                    },
                                )
                            }
                        }
                        Spacer(Modifier.width(4.dp))
                        IconButton(onClick = onStop, modifier = Modifier.size(38.dp)) {
                            Icon(
                                Icons.Outlined.StopCircle,
                                contentDescription = "Stop",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                    FilledIconButton(
                        onClick = {
                            if (draft.isNotBlank() || attachments.isNotEmpty()) {
                                onSend(
                                    draft,
                                    attachments,
                                    if (busy) delivery else null,
                                )
                                onDraftChange("")
                                onAttachmentsChange(emptyList())
                            }
                        },
                        enabled = (draft.isNotBlank() || attachments.isNotEmpty()) && !sending,
                        shape = RoundedCornerShape(13.dp),
                        modifier = Modifier.size(40.dp),
                    ) {
                        if (sending) {
                            CircularProgressIndicator(
                                Modifier.size(17.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Icon(
                                Icons.AutoMirrored.Outlined.Send,
                                contentDescription = "Send",
                                modifier = Modifier.size(19.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachmentChip(attachment: OutgoingAttachment, onRemove: () -> Unit) {
    val isImage = attachment.mime.startsWith("image/")
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.widthIn(max = 220.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 4.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isImage) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(Uri.parse(attachment.uri))
                        .build(),
                    contentDescription = attachment.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(9.dp)),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Description,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(7.dp))
            Text(
                text = attachment.name,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = "Remove attachment",
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

private suspend fun readAttachment(context: Context, uri: Uri): OutgoingAttachment =
    withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        var name = "attachment"
        var size = -1L
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0) name = cursor.getString(nameIndex) ?: name
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
            }
        }
        if (size > MAX_ATTACHMENT_BYTES) {
            throw IllegalArgumentException("Files must be smaller than 10 MB.")
        }
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalArgumentException("Could not open that file.")
        if (bytes.size > MAX_ATTACHMENT_BYTES) {
            throw IllegalArgumentException("Files must be smaller than 10 MB.")
        }
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        OutgoingAttachment(
            uri = uri.toString(),
            name = name,
            mime = mime,
            base64 = Base64.encodeToString(bytes, Base64.NO_WRAP),
        )
    }
