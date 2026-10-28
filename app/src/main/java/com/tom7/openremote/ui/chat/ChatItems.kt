package com.tom7.openremote.ui.chat

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.automirrored.outlined.CallSplit
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tom7.openremote.data.AssistantPart
import com.tom7.openremote.data.FormInfo
import com.tom7.openremote.data.PermissionRequest
import com.tom7.openremote.data.PromptFile
import com.tom7.openremote.data.SessionMessage
import com.tom7.openremote.data.ToolContent
import com.tom7.openremote.data.ToolState
import com.tom7.openremote.ui.common.Markdown
import com.tom7.openremote.ui.common.clockTime
import com.tom7.openremote.ui.common.prettyJson
import com.tom7.openremote.ui.common.rememberClipboardWriter
import com.tom7.openremote.ui.common.relativeTime
import com.tom7.openremote.ui.common.toolDisplayName
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

@Composable
fun TimelineMessage(
    message: SessionMessage,
    busy: Boolean,
    showReasoning: Boolean,
    onOpenSession: (String) -> Unit,
) {
    when (message.type) {
        "user" -> UserMessage(message, pending = false)
        "assistant" -> AssistantMessage(message, busy, showReasoning, onOpenSession)
        "system", "synthetic" -> NoteMessage(
            text = message.description ?: message.text.orEmpty(),
            synthetic = message.type == "synthetic",
        )

        "skill" -> NoteMessage(
            text = buildString {
                append("Skill activated · ")
                append(message.name ?: message.skill.orEmpty())
                message.text?.takeIf { it.isNotBlank() }?.let { append("\n").append(it.take(240)) }
            },
            synthetic = true,
        )

        "shell" -> ShellMessage(message)
        "compaction" -> CompactionMessage(message)
        "agent-switched" -> SwitchNote("Agent · ${message.agent.orEmpty()}")
        "model-switched" -> SwitchNote(
            "Model · ${message.model?.providerID.orEmpty()}/${message.model?.id.orEmpty()}",
        )

        "location-switched" -> SwitchNote("Moved to ${message.location?.directory.orEmpty()}")
        "idle" -> IdleMessage(message)
    }
}

@Composable
fun UserMessage(
    message: SessionMessage,
    pending: Boolean,
    onSteer: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
) {
    val copy = rememberClipboardWriter()
    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 620.dp)
                .align(Alignment.CenterEnd),
            horizontalAlignment = Alignment.End,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.13f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = RoundedCornerShape(18.dp, 18.dp, 5.dp, 18.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)),
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
                    val text = message.displayText
                    if (text.isNotBlank()) {
                        Text(text = text, style = MaterialTheme.typography.bodyLarge)
                    }
                    message.files.forEach { file ->
                        if (file.mime.startsWith("image/") && file.data.isNotBlank()) {
                            InlineImageAttachment(file)
                            return@forEach
                        }
                        Row(
                            modifier = Modifier.padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Outlined.AttachFile,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = file.name ?: file.mime.ifBlank { "attachment" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = when {
                        !pending -> relativeTime(message.time.created)
                        message.delivery == "queue" -> "queued · starts after this run"
                        message.delivery == "steer" -> "steering · applies at the next step"
                        else -> "sending…"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (message.displayText.isNotBlank()) {
                    IconButton(
                        onClick = { copy(message.displayText) },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            Icons.Outlined.ContentCopy,
                            contentDescription = "Copy message",
                            modifier = Modifier.size(13.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (onSteer != null || onCancel != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (onSteer != null) {
                        TextButton(
                            onClick = onSteer,
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            modifier = Modifier.height(30.dp),
                        ) {
                            Text("Steer now", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    if (onCancel != null) {
                        TextButton(
                            onClick = onCancel,
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            modifier = Modifier.height(30.dp),
                        ) {
                            Text(
                                "Cancel",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InlineImageAttachment(file: PromptFile) {
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, file.data) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            decodeInlineImage(file.data)
        }
    }
    val image = bitmap
    if (image != null) {
        Image(
            bitmap = image,
            contentDescription = file.name ?: "Attached image",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .padding(top = 8.dp)
                .widthIn(max = 220.dp)
                .clip(RoundedCornerShape(12.dp)),
        )
        return
    }
    Row(
        modifier = Modifier.padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.AttachFile,
            contentDescription = null,
            modifier = Modifier.size(15.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = file.name ?: file.mime,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun decodeInlineImage(data: String): androidx.compose.ui.graphics.ImageBitmap? = runCatching {
    val bytes = android.util.Base64.decode(data, android.util.Base64.DEFAULT)
    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    val largest = maxOf(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
    var sample = 1
    while (largest / sample > 1_280) sample *= 2
    val options = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
}.getOrNull()

@Composable
fun AssistantMessage(
    message: SessionMessage,
    busy: Boolean,
    showReasoning: Boolean,
    onOpenSession: (String) -> Unit,
) {
    val copy = rememberClipboardWriter()
    val text = message.content.filter { it.type == "text" }.mapNotNull { it.text }.joinToString("\n\n")
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                modifier = Modifier.size(22.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = message.agent ?: "OpenCode",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            message.model?.let { model ->
                Text(
                    text = " · ${model.id}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.weight(1f))
            if (text.isNotBlank()) {
                IconButton(
                    onClick = { copy(text) },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        Icons.Outlined.ContentCopy,
                        contentDescription = "Copy response",
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            message.content.forEach { part ->
                when (part.type) {
                    "text" -> part.text?.takeIf { it.isNotBlank() }?.let { Markdown(it) }
                    "reasoning" -> if (showReasoning) ReasoningCard(part)
                    "tool" -> ToolCard(part, onOpenSession)
                }
            }
            if (message.content.isEmpty() && message.error == null) {
                if (busy && message.time.completed == null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Thinking…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            message.error?.let { error ->
                ErrorCard(
                    title = if (error.type.isBlank()) "Request failed" else error.type,
                    message = error.message,
                )
            }
            message.retry?.let { retry ->
                Text(
                    text = "Retry ${retry.attempt} · ${retry.error?.message.orEmpty()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (message.finish == "error" && message.error == null) {
                ErrorCard(title = "Request failed", message = "The model returned an error.")
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = clockTime(message.time.created),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            )
            message.cost?.takeIf { it > 0 }?.let { cost ->
                Text(
                    text = " · $${"%.3f".format(cost)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                )
            }
        }
    }
}

@Composable
fun ReasoningCard(part: AssistantPart) {
    var expanded by remember(part.text) { mutableStateOf(false) }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable { expanded = !expanded },
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Build,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    "Reasoning",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = part.text.orEmpty(),
                    style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun ToolCard(part: AssistantPart, onOpenSession: (String) -> Unit) {
    val state = part.state ?: ToolState()
    var expanded by remember { mutableStateOf(false) }
    val name = part.name ?: "tool"
    val status = state.status
    val running = status == "running" || status == "streaming"
    val colors = MaterialTheme.colorScheme
    val subagentSession = subagentSessionID(part)

    Surface(
        color = colors.surface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            1.dp,
            if (status == "error") colors.error.copy(alpha = 0.5f) else colors.outlineVariant,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = toolIcon(name),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (running) colors.primary else colors.onSurfaceVariant,
                )
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = toolDisplayName(name),
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.width(8.dp))
                        ToolStatus(status)
                    }
                    val summary = toolSummary(name, state)
                    if (summary.isNotBlank()) {
                        Text(
                            text = summary,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = colors.onSurfaceVariant,
                            maxLines = if (expanded) 6 else 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (running) {
                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        modifier = Modifier.size(18.dp),
                        tint = colors.onSurfaceVariant,
                    )
                }
            }

            if (expanded) {
                Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                    val input = prettyJson(state.input)
                    if (input.isNotBlank()) {
                        ToolSection("Input", input)
                    }
                    val content = toolContentText(state.content)
                    if (content.isNotBlank()) {
                        ToolSection("Result", content)
                    }
                    state.error?.let { error ->
                        ToolSection("Error", error.message, error = true)
                    }
                }
            }

            subagentSession?.let { id ->
                TextButton(
                    onClick = { onOpenSession(id) },
                    modifier = Modifier.padding(start = 6.dp, bottom = 4.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Outlined.CallSplit,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(7.dp))
                    Text("Open subagent session")
                }
            }
        }
    }
}

private val SESSION_ID_PATTERN = Regex("ses_[A-Za-z0-9]+")

/** The subagent tool reports the child session it created; surface it so the conversation is reachable. */
private fun subagentSessionID(part: AssistantPart): String? {
    if (part.name != "subagent" && part.name != "task") return null
    val state = part.state ?: return null
    val text = buildString {
        state.content?.forEach { content -> content.text?.let { append(it).append('\n') } }
        state.metadata?.let { append(it.toString()) }
    }
    return SESSION_ID_PATTERN.find(text)?.value
}

@Composable
private fun ToolStatus(status: String) {
    val colors = MaterialTheme.colorScheme
    val (label, color) = when (status) {
        "completed" -> "done" to colors.tertiary
        "error" -> "failed" to colors.error
        "running" -> "running" to colors.primary
        "streaming" -> "preparing" to colors.primary
        else -> status to colors.onSurfaceVariant
    }
    Surface(
        color = color.copy(alpha = 0.13f),
        shape = RoundedCornerShape(6.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
        )
    }
}

@Composable
private fun ToolSection(label: String, text: String, error: Boolean = false) {
    Spacer(Modifier.height(10.dp))
    Text(
        label.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        shape = RoundedCornerShape(9.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    ) {
        Text(
            text = text.take(20_000),
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(10.dp),
        )
    }
}

@Composable
private fun ErrorCard(title: String, message: String) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(12.dp)) {
            Icon(Icons.Outlined.ErrorOutline, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(9.dp))
            Column {
                Text(title, style = MaterialTheme.typography.labelLarge)
                if (message.isNotBlank()) {
                    Text(message, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun NoteMessage(text: String, synthetic: Boolean) {
    if (text.isBlank()) return
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (synthetic) Icons.Outlined.AutoAwesome else Icons.Outlined.Info,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SwitchNote(text: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            shape = RoundedCornerShape(20.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun IdleMessage(message: SessionMessage) {
    when (message.outcome) {
        "failed" -> ErrorCard(
            title = "Run failed",
            message = message.error?.message ?: "The last run failed.",
        )

        "interrupted" -> NoteMessage("Interrupted", synthetic = false)
        else -> Unit
    }
}

@Composable
private fun ShellMessage(message: SessionMessage) {
    var expanded by remember { mutableStateOf(false) }
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.Terminal,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "$ ${message.command.orEmpty()}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.weight(1f),
                    maxLines = if (expanded) 8 else 1,
                    overflow = TextOverflow.Ellipsis,
                )
                ToolStatus(message.status ?: "exited")
            }
            val output = message.output?.output.orEmpty()
            if (output.isNotBlank()) {
                Text(
                    text = output.trim(),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (expanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun CompactionMessage(message: SessionMessage) {
    val running = message.status == "running"
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            if (running) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
            } else {
                Icon(
                    Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = MaterialTheme.colorScheme.tertiary,
                )
            }
            Spacer(Modifier.width(9.dp))
            Column {
                Text(
                    text = if (running) "Compacting context" else "Context compacted",
                    style = MaterialTheme.typography.labelMedium,
                )
                message.summary?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 3.dp),
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
fun PermissionCard(
    request: PermissionRequest,
    onReply: (String) -> Unit,
) {
    var expanded by remember(request.id) { mutableStateOf(false) }
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                    shape = CircleShape,
                    modifier = Modifier.size(26.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Outlined.Check,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Permission needed", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = request.action,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (request.message?.isNotBlank() == true) {
                Text(
                    text = request.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            val resources = if (expanded) request.resources else request.resources.take(3)
            resources.forEach { resource ->
                Text(
                    text = resource,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = if (expanded) 8 else 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 3.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            RoundedCornerShape(6.dp),
                        )
                        .padding(horizontal = 7.dp, vertical = 4.dp),
                )
            }
            if (request.resources.size > 3) {
                TextButton(onClick = { expanded = !expanded }, modifier = Modifier.height(30.dp)) {
                    Text(if (expanded) "Show less" else "+${request.resources.size - 3} more")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(
                    onClick = { onReply("once") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f),
                ) { Text("Allow once") }
                TextButton(
                    onClick = { onReply("always") },
                    shape = RoundedCornerShape(10.dp),
                ) { Text("Always") }
                TextButton(
                    onClick = { onReply("reject") },
                    shape = RoundedCornerShape(10.dp),
                ) { Text("Reject", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
fun FormCard(form: FormInfo, onOpen: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.Description,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(form.title.ifBlank { "Input requested" }, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = if (form.fields.size == 1) "1 field" else "${form.fields.size} fields",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalButton(onClick = onOpen, shape = RoundedCornerShape(10.dp)) {
                Text("Answer")
            }
        }
    }
}

private fun toolIcon(name: String) = when (name.substringAfterLast('.')) {
    "bash", "shell" -> Icons.Outlined.Terminal
    "edit", "write", "patch" -> Icons.Outlined.Edit
    "grep", "glob" -> Icons.Outlined.Search
    "webfetch", "websearch" -> Icons.Outlined.Public
    "read" -> Icons.Outlined.Description
    else -> Icons.Outlined.Build
}

private fun toolSummary(name: String, state: ToolState): String {
    val input = state.input
    if (input is JsonPrimitive) return input.contentOrNull.orEmpty().take(400)
    val obj = input as? JsonObject ?: return ""
    val keys = listOf(
        "description", "command", "filePath", "path", "pattern", "query", "url",
        "prompt", "content", "objective",
    )
    for (key in keys) {
        val value = obj[key]?.jsonPrimitive?.contentOrNull
        if (!value.isNullOrBlank()) {
            return when (key) {
                "filePath", "path" -> value
                "pattern" -> "\"$value\""
                else -> value.lineSequence().firstOrNull()?.take(400).orEmpty()
            }
        }
    }
    return ""
}

private fun toolContentText(content: List<ToolContent>?): String {
    if (content.isNullOrEmpty()) return ""
    return content.mapNotNull { part ->
        when (part.type) {
            "text" -> part.text
            "file" -> part.name ?: part.uri
            else -> null
        }
    }.joinToString("\n\n").trim()
}
