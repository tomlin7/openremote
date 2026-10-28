package com.tom7.openremote.ui.pickers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tom7.openremote.data.AgentInfo
import com.tom7.openremote.data.CommandInfo
import com.tom7.openremote.data.ModelInfo
import com.tom7.openremote.data.ModelRef
import com.tom7.openremote.data.ProviderInfo
import com.tom7.openremote.ui.common.formatTokens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelPickerSheet(
    models: List<ModelInfo>,
    providers: List<ProviderInfo>,
    selected: ModelRef?,
    onDismiss: () -> Unit,
    onSelect: (ModelRef) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf<String?>(null) }

    val providerNames = remember(providers) { providers.associate { it.id to it.name } }
    val filtered = remember(models, query) {
        if (query.isBlank()) models
        else models.filter {
            it.name.contains(query, ignoreCase = true) ||
                it.modelID.contains(query, ignoreCase = true) ||
                it.providerID.contains(query, ignoreCase = true)
        }
    }
    val grouped = remember(filtered) {
        filtered.groupBy { it.providerID }
            .toList()
            .sortedBy { (id, _) -> providerNames[id] ?: id }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.fillMaxHeight(0.88f)) {
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Text("Model", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Choose the model for this session.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search models") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
            }

            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                grouped.forEach { (providerID, providerModels) ->
                    item(key = "provider:$providerID") {
                        Text(
                            text = providerNames[providerID] ?: providerID,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        )
                    }
                    items(providerModels, key = { it.providerID + "/" + it.modelID }) { model ->
                        val isSelected = selected?.id == model.modelID && selected.providerID == model.providerID
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (model.variants.isEmpty()) {
                                        onSelect(model.ref())
                                    } else {
                                        expanded = if (expanded == model.modelID) null else model.modelID
                                    }
                                }
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(model.displayName, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        text = buildString {
                                            append(model.modelID)
                                            model.limit?.context?.let { append(" · ").append(formatTokens(it)).append(" ctx") }
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                if (isSelected && model.variants.isEmpty()) {
                                    Icon(
                                        Icons.Outlined.Check,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                } else if (model.variants.isNotEmpty()) {
                                    Text(
                                        text = if (expanded == model.modelID) "Hide variants" else "Variants",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                            if (expanded == model.modelID) {
                                Row(
                                    modifier = Modifier.padding(top = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    AssistChip(
                                        onClick = { onSelect(model.ref()) },
                                        label = { Text("default") },
                                    )
                                    model.variants.forEach { variant ->
                                        AssistChip(
                                            onClick = { onSelect(model.ref(variant.id)) },
                                            label = { Text(variant.id) },
                                            leadingIcon = if (
                                                isSelected && selected?.variant == variant.id
                                            ) {
                                                { Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.width(16.dp)) }
                                            } else null,
                                        )
                                    }
                                }
                            }
                        }
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            modifier = Modifier.padding(horizontal = 20.dp),
                        )
                    }
                }
                if (grouped.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text("No models match", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentPickerSheet(
    agents: List<AgentInfo>,
    selected: String?,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    val filtered = remember(agents, query) {
        if (query.isBlank()) agents
        else agents.filter {
            it.name.contains(query, ignoreCase = true) || it.id.contains(query, ignoreCase = true)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.fillMaxHeight(0.82f)) {
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Text("Agent", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Agents decide which tools and permissions OpenCode uses.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search agents") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
            }
            LazyColumn {
                items(filtered, key = { it.id }) { agent ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(agent.id) }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = parseColor(agent.color) ?: MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            modifier = Modifier.width(9.dp).height(9.dp),
                        ) {}
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(agent.name, style = MaterialTheme.typography.titleSmall)
                            agent.description?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        if (agent.id == selected) {
                            Icon(
                                Icons.Outlined.Check,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommandPickerSheet(
    commands: List<CommandInfo>,
    onDismiss: () -> Unit,
    onSelect: (CommandInfo) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    val filtered = remember(commands, query) {
        if (query.isBlank()) commands
        else commands.filter {
            it.name.contains(query, ignoreCase = true) ||
                it.description?.contains(query, ignoreCase = true) == true
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.fillMaxHeight(0.8f)) {
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Text("Commands", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Slash commands run prepared prompts and workflows.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search commands") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
            }
            if (commands.isEmpty()) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(32.dp).width(24.dp).height(24.dp),
                    strokeWidth = 2.dp,
                )
            }
            LazyColumn {
                items(filtered, key = { it.name }) { command ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(command) }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                    ) {
                        Text("/${command.name}", style = MaterialTheme.typography.titleSmall)
                        command.description?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

private fun parseColor(value: String?): Color? {
    if (value.isNullOrBlank()) return null
    return runCatching {
        val hex = value.removePrefix("#")
        Color(android.graphics.Color.parseColor("#$hex"))
    }.getOrNull()
}
