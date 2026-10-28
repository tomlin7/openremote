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
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tom7.openremote.data.AssistantPart
import com.tom7.openremote.data.FormInfo
import com.tom7.openremote.data.MessageTime
import com.tom7.openremote.data.PermissionRequest
import com.tom7.openremote.data.PromptFile
import com.tom7.openremote.data.SessionMessage
import com.tom7.openremote.data.ToolContent
import com.tom7.openremote.data.ToolState
import com.tom7.openremote.ui.common.Markdown
import com.tom7.openremote.ui.common.prettyJson
import com.tom7.openremote.ui.common.rememberClipboardWriter
import com.tom7.openremote.ui.common.relativeTime
import com.tom7.openremote.ui.common.toolDisplayName
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

// Desktop transcripts keep tool rows on one 13px line: a medium title and a muted subtitle.
private val RowTitleStyle = TextStyle(
    fontSize = 13.sp,
    lineHeight = 18.sp,
    fontWeight = FontWeight.Medium,
)

private val RowSubtitleStyle = TextStyle(
    fontSize = 13.sp,
    lineHeight = 18.sp,
)

private val RowBodyStyle = TextStyle(
    fontSize = 12.sp,
    lineHeight = 18.sp,
    fontFamily = FontFamily.Monospace,
)

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
        "system", "synthetic" -> NoteMessage(text = message.description ?: message.text.orEmpty())

        "skill" -> NoteMessage(
            text = buildString {
                append("Skill activated · ")
                append(message.name ?: message.skill.orEmpty())
                message.text?.takeIf { it.isNotBlank() }?.let { append("\n").append(it.take(240)) }
            },
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
                .widthIn(max = 560.dp)
                .align(Alignment.CenterEnd),
            horizontalAlignment = Alignment.End,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = RoundedCornerShape(10.dp),
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    val text = message.displayText
                    if (text.isNotBlank()) {
                        Text(text = text, style = MaterialTheme.typography.bodyMedium)
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
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        message.content.forEach { part ->
            when (part.type) {
                "text" -> part.text?.takeIf { it.isNotBlank() }?.let { Markdown(it) }
                "reasoning" -> if (showReasoning) ReasoningRow(part)
                "tool" -> ToolRow(part, onOpenSession)
            }
        }
        if (message.content.isEmpty() && message.error == null) {
            if (busy && message.time.completed == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Thinking…",
                        style = RowSubtitleStyle,
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
}

@Composable
private fun ReasoningRow(part: AssistantPart) {
    val text = part.text.orEmpty()
    var expanded by remember(part.id, text) { mutableStateOf(false) }
    val streaming = part.time?.completed == null
    val duration = reasoningDuration(part.time)

    Column(modifier = Modifier.fillMaxWidth().animateContentSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = text.isNotBlank()) { expanded = !expanded }
                .padding(vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (streaming) "Thinking" else "Thought",
                style = RowTitleStyle,
                color = if (streaming) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
            )
            if (!streaming && duration != null) {
                Spacer(Modifier.width(7.dp))
                Text(
                    text = duration,
                    style = RowSubtitleStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            if (text.isNotBlank()) {
                Spacer(Modifier.width(7.dp))
                ExpandChevron(expanded)
            }
        }
        if (expanded) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(start = 14.dp, top = 2.dp, bottom = 2.dp),
            )
        }
    }
}

private fun reasoningDuration(time: MessageTime?): String? {
    val start = time?.created ?: return null
    val end = time.completed ?: return null
    val total = ((end - start) / 1000).coerceAtLeast(0)
    // Sub-second reasoning is the common case; printing "0s" on every step is noise.
    if (total < 1) return null
    if (total < 60) return "${total}s"
    return "${total / 60}m ${total % 60}s"
}

@Composable
private fun ExpandChevron(expanded: Boolean) {
    Icon(
        if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
        contentDescription = if (expanded) "Collapse" else "Expand",
        modifier = Modifier.size(14.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
    )
}

@Composable
fun ToolRow(part: AssistantPart, onOpenSession: (String) -> Unit) {
    val state = part.state ?: ToolState()
    val name = part.name ?: "tool"
    var expanded by remember(part.id) { mutableStateOf(false) }
    val summary = toolSummary(name, state)
    val input = if (toolShowsInput(name)) prettyJson(state.input) else ""
    val result = toolContentText(state.content)
    val error = state.error?.message.orEmpty()
    val hasDetails = input.isNotBlank() || result.isNotBlank() || error.isNotBlank()
    val subagentSession = subagentSessionID(part)

    Column(modifier = Modifier.fillMaxWidth().animateContentSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = hasDetails) { expanded = !expanded }
                .padding(vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = toolTitle(name),
                style = RowTitleStyle,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            if (summary.isNotBlank()) {
                Spacer(Modifier.width(7.dp))
                Text(
                    text = summary,
                    style = RowSubtitleStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            if (hasDetails) {
                Spacer(Modifier.width(7.dp))
                ExpandChevron(expanded)
            }
        }
        if (expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, top = 2.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (input.isNotBlank()) ToolSection("Input", input)
                if (result.isNotBlank()) ToolSection("Result", result)
                if (error.isNotBlank()) ToolSection("Error", error, error = true)
            }
        }
        subagentSession?.let { id ->
            TextButton(
                onClick = { onOpenSession(id) },
                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                modifier = Modifier.height(30.dp),
            ) {
                Text("Open subagent session", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun toolTitle(name: String): String = when (name.substringAfterLast('.')) {
    "websearch" -> "Web Search"
    "webfetch" -> "Web Fetch"
    "subagent", "task" -> "Agent"
    "todowrite", "todo" -> "Todos"
    else -> toolDisplayName(name)
}

// The row subtitle already names the tool's meaningful input. Repeating it as raw
// JSON on expand is noise; only file edits need their payload shown.
private fun toolShowsInput(name: String): Boolean = when (name.substringAfterLast('.')) {
    "shell", "execute", "read", "list", "glob", "grep", "webfetch", "websearch", "subagent", "task", "skill" -> false
    else -> true
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
private fun ToolSection(label: String, text: String, error: Boolean = false) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(3.dp))
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = text.take(20_000),
                style = RowBodyStyle,
                color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
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
private fun NoteMessage(text: String) {
    if (text.isBlank()) return
    Text(
        text = text,
        style = RowSubtitleStyle,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
    )
}

@Composable
private fun SwitchNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}

@Composable
private fun IdleMessage(message: SessionMessage) {
    when (message.outcome) {
        "failed" -> ErrorCard(
            title = "Run failed",
            message = message.error?.message ?: "The last run failed.",
        )

        "interrupted" -> NoteMessage("Interrupted")
        else -> Unit
    }
}

@Composable
private fun ShellMessage(message: SessionMessage) {
    var expanded by remember(message.id) { mutableStateOf(false) }
    val output = message.output?.output.orEmpty()
    val status = shellStatus(message)
    val hasDetails = output.isNotBlank()

    Column(modifier = Modifier.fillMaxWidth().animateContentSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = hasDetails) { expanded = !expanded }
                .padding(vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Shell",
                style = RowTitleStyle,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            val command = message.command.orEmpty()
            if (command.isNotBlank()) {
                Spacer(Modifier.width(7.dp))
                Text(
                    text = command,
                    style = RowSubtitleStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            status?.let {
                Spacer(Modifier.width(7.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 1,
                )
            }
            if (hasDetails) {
                Spacer(Modifier.width(7.dp))
                ExpandChevron(expanded)
            }
        }
        if (expanded) {
            Text(
                text = output.trim(),
                style = RowBodyStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, top = 2.dp, bottom = 4.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        RoundedCornerShape(8.dp),
                    )
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
    }
}

private fun shellStatus(message: SessionMessage): String? = when {
    message.status == "timeout" -> "timeout"
    message.status == "killed" -> "cancelled"
    message.status == "exited" && message.exit != null && message.exit != 0.0 ->
        "exit ${message.exit.toInt()}"

    else -> null
}

@Composable
private fun CompactionMessage(message: SessionMessage) {
    val running = message.status == "running"
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            text = if (running) "Compacting context…" else "Context compacted",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        message.summary?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
            )
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

private fun toolSummary(name: String, state: ToolState): String {
    val input = state.input
    if (input is JsonPrimitive) return input.contentOrNull.orEmpty().take(400)
    val obj = input as? JsonObject ?: return ""
    val tool = name.substringAfterLast('.')

    fun field(key: String): String? = obj[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

    fun firstLine(key: String): String? = field(key)?.lineSequence()?.firstOrNull()?.trim()?.take(400)

    val path = field("path") ?: field("filePath")
    val filename = path?.let { it.substringAfterLast('\\').substringAfterLast('/') }

    return when (tool) {
        "shell" -> firstLine("command").orEmpty()
        "execute" -> firstLine("code").orEmpty()
        "read" -> listOfNotNull(
            filename,
            field("offset")?.let { "offset=$it" },
            field("limit")?.let { "limit=$it" },
        ).joinToString(" ")

        "grep" -> listOfNotNull(
            field("pattern")?.let { "\"$it\"" },
            field("include")?.let { "· $it" },
        ).joinToString(" ")

        "glob" -> field("pattern")?.let { "\"$it\"" }.orEmpty()
        "list" -> path.orEmpty()
        "edit", "write", "patch" -> filename ?: path.orEmpty()
        "webfetch" -> field("url").orEmpty()
        "websearch" -> field("query").orEmpty()
        "subagent", "task" -> firstLine("description").orEmpty()
        "skill" -> field("name") ?: field("id").orEmpty()
        else -> firstLine("description")
            ?: firstLine("command")
            ?: field("name")
            ?: field("query")
            ?: path.orEmpty()
    }
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
