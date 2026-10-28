package com.tom7.openremote.ui.sessions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tom7.openremote.data.AgentInfo
import com.tom7.openremote.data.ProjectInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewSessionSheet(
    projects: List<ProjectInfo>,
    defaultDirectory: String,
    agents: List<AgentInfo>,
    creating: Boolean,
    onDismiss: () -> Unit,
    onCreate: (directory: String, title: String?, agent: String?) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var title by remember { mutableStateOf("") }
    var directory by remember(defaultDirectory, projects) {
        mutableStateOf(
            projects.firstOrNull { it.canonical == defaultDirectory }?.canonical
                ?: projects.firstOrNull()?.canonical
                ?: defaultDirectory,
        )
    }
    var agent by remember(agents) {
        mutableStateOf(agents.firstOrNull { it.id == "build" }?.id ?: agents.firstOrNull()?.id)
    }
    var projectMenu by remember { mutableStateOf(false) }
    var agentMenu by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("New session", style = MaterialTheme.typography.headlineSmall)
            Text(
                "OpenCode will work in the selected project on your computer.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title (optional)") },
                placeholder = { Text("Named automatically after the first prompt") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Column {
                Text("Project", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(6.dp))
                androidx.compose.foundation.layout.Box {
                    OutlinedButton(
                        onClick = { projectMenu = true },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = directory.ifBlank { "Server default" },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = null)
                    }
                    DropdownMenu(expanded = projectMenu, onDismissRequest = { projectMenu = false }) {
                        projects.forEach { project ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(project.displayName)
                                        Text(
                                            project.canonical,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                },
                                onClick = {
                                    directory = project.canonical
                                    projectMenu = false
                                },
                            )
                        }
                        if (projects.isEmpty() && defaultDirectory.isNotBlank()) {
                            DropdownMenuItem(
                                text = { Text(defaultDirectory) },
                                onClick = { projectMenu = false },
                            )
                        }
                    }
                }
            }

            Column {
                Text("Agent", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(6.dp))
                androidx.compose.foundation.layout.Box {
                    OutlinedButton(
                        onClick = { agentMenu = true },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = agents.firstOrNull { it.id == agent }?.name ?: "Server default",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = null)
                    }
                    DropdownMenu(expanded = agentMenu, onDismissRequest = { agentMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Server default") },
                            onClick = {
                                agent = null
                                agentMenu = false
                            },
                        )
                        agents.forEach { item ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(item.name)
                                        item.description?.let {
                                            Text(
                                                it,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    agent = item.id
                                    agentMenu = false
                                },
                            )
                        }
                    }
                }
            }

            Button(
                onClick = {
                    onCreate(
                        directory,
                        title.trim().takeIf { it.isNotBlank() },
                        agent,
                    )
                },
                enabled = !creating && directory.isNotBlank(),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) {
                if (creating) {
                    CircularProgressIndicator(
                        modifier = Modifier.width(18.dp).height(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Text(if (creating) "Creating" else "Create session")
            }
        }
    }
}
