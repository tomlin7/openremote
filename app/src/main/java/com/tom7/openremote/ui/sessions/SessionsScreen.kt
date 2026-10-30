package com.tom7.openremote.ui.sessions

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tom7.openremote.AppViewModel
import com.tom7.openremote.data.OpenCodeApi
import com.tom7.openremote.data.SessionInfo
import com.tom7.openremote.ui.common.relativeTime
import com.tom7.openremote.ui.common.sessionSubtitle

enum class SessionsViewMode { ByDate, ByProject }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionsScreen(
    api: OpenCodeApi,
    app: AppViewModel,
    onOpenSession: (SessionInfo) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: SessionsViewModel = viewModel(
        key = "sessions:${app.apiKey}",
        factory = viewModelFactory {
            initializer { SessionsViewModel(api, app.events) }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    var searchOpen by remember { mutableStateOf(false) }
    var showNewSession by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<SessionInfo?>(null) }
    var deleteTarget by remember { mutableStateOf<SessionInfo?>(null) }
    var menuTarget by remember { mutableStateOf<String?>(null) }
    var viewMode by remember { mutableStateOf(SessionsViewMode.ByDate) }

    LaunchedEffect(state.toast) {
        state.toast?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeToast()
        }
    }

    val grouped = remember(state.sessions, viewMode) {
        when (viewMode) {
            SessionsViewMode.ByDate -> groupByDate(state.sessions)
            SessionsViewMode.ByProject -> groupByProject(state.sessions)
        }
    }
    val allGroupKeys = remember(grouped) { grouped.map { it.first }.toSet() }
    var expandedGroups by remember(viewMode) { mutableStateOf(allGroupKeys) }

    LaunchedEffect(allGroupKeys) {
        expandedGroups = expandedGroups.union(allGroupKeys)
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    if (searchOpen) {
                        OutlinedTextField(
                            value = state.search,
                            onValueChange = viewModel::setSearch,
                            placeholder = { Text("Search") },
                            singleLine = true,
                            trailingIcon = {
                                IconButton(onClick = { searchOpen = false; viewModel.setSearch("") }) {
                                    Icon(Icons.Outlined.Close, contentDescription = "Close search")
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Text("Sessions", style = MaterialTheme.typography.titleLarge)
                    }
                },
                actions = {
                    if (!searchOpen) {
                        IconButton(onClick = { searchOpen = true }) {
                            Icon(Icons.Outlined.Search, contentDescription = "Search sessions")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showNewSession = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Outlined.Add, contentDescription = "New session")
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.refreshing && !state.loading,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            val listState = rememberLazyListState()
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 12.dp,
                    bottom = 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                if (state.loading && state.sessions.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(top = 80.dp), contentAlignment = Alignment.Center) {
                            Text(
                                "Loading…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else if (state.sessions.isEmpty()) {
                    item {
                        EmptySessions(
                            archived = state.archived,
                            onNew = { showNewSession = true },
                            modifier = Modifier.padding(top = 72.dp),
                        )
                    }
                }
                grouped.forEach { (label, sessions) ->
                    val expanded = label in expandedGroups
                    item(key = "header:$label") {
                        GroupHeader(
                            label = label,
                            count = sessions.size,
                            expanded = expanded,
                            onClick = {
                                expandedGroups = if (expanded) expandedGroups - label else expandedGroups + label
                            },
                        )
                    }
                    if (expanded) {
                        items(sessions, key = { it.id }) { session ->
                            SessionRow(
                                session = session,
                                busy = session.id in state.busy,
                                menuOpen = menuTarget == session.id,
                                onOpen = { onOpenSession(session) },
                                onMenu = { menuTarget = if (menuTarget == session.id) null else session.id },
                                onDismissMenu = { menuTarget = null },
                                onRename = { menuTarget = null; renameTarget = session },
                                onArchive = {
                                    menuTarget = null
                                    viewModel.archive(session, !state.archived)
                                },
                                onDelete = { menuTarget = null; deleteTarget = session },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showNewSession) {
        NewSessionSheet(
            projects = state.projects,
            defaultDirectory = state.defaultDirectory,
            agents = state.agents,
            creating = state.creating,
            onDismiss = { showNewSession = false },
            onCreate = { directory, title, agent ->
                viewModel.createSession(directory, title, agent, null) { session ->
                    showNewSession = false
                    onOpenSession(session)
                }
            },
        )
    }

    renameTarget?.let { session ->
        RenameDialog(
            session = session,
            onDismiss = { renameTarget = null },
            onRename = { title ->
                viewModel.rename(session, title)
                renameTarget = null
            },
        )
    }

    deleteTarget?.let { session ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete session?") },
            text = { Text("\"${session.displayTitle}\" will be permanently deleted.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(session)
                        deleteTarget = null
                    },
                ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun GroupHeader(
    label: String,
    count: Int,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 0f else -90f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "chevronRotation"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.ExpandMore,
            contentDescription = null,
            modifier = Modifier
                .size(18.dp)
                .rotate(rotation),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        )
    }
}

@Composable
private fun SessionRow(
    session: SessionInfo,
    busy: Boolean,
    menuOpen: Boolean,
    onOpen: () -> Unit,
    onMenu: () -> Unit,
    onDismissMenu: () -> Unit,
    onRename: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Grip handle or pixel animation when busy
        if (busy) {
            BusyPixelIndicator()
        } else {
            Icon(
                Icons.Outlined.DragHandle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = session.displayTitle,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (busy) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (busy) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = sessionSubtitle(session),
                style = MaterialTheme.typography.labelSmall,
                color = if (busy) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box {
            IconButton(onClick = onMenu, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Outlined.MoreVert,
                    contentDescription = "Session actions",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(18.dp),
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = onDismissMenu) {
                DropdownMenuItem(
                    text = { Text("Rename") },
                    onClick = onRename,
                    leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                )
                DropdownMenuItem(
                    text = { Text(if (session.isArchived) "Restore" else "Archive") },
                    onClick = onArchive,
                    leadingIcon = {
                        Icon(
                            if (session.isArchived) Icons.Outlined.Unarchive else Icons.Outlined.Archive,
                            contentDescription = null,
                        )
                    },
                )
                DropdownMenuItem(
                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                    onClick = onDelete,
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                    },
                )
            }
        }
    }
}

@Composable
internal fun BusyPixelIndicator(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "busy-pixels")
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    
    // 3x3 grid with spiral animation - center pixel always active
    // Pattern: spiral from center outward
    Box(modifier = modifier.size(13.dp), contentAlignment = Alignment.Center) {
        Column(
            verticalArrangement = Arrangement.spacedBy(1.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Row 0: corners first, then edges
            Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                // Top-left corner
                PixelSquare(primary, transition, delay = 600)
                // Top edge
                PixelSquare(tertiary, transition, delay = 300)
                // Top-right corner
                PixelSquare(primary, transition, delay = 900)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                // Left edge
                PixelSquare(tertiary, transition, delay = 450)
                // Center - always active with breathing
                PixelSquare(primary, transition, delay = 0, breathing = true)
                // Right edge
                PixelSquare(tertiary, transition, delay = 750)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                // Bottom-left corner
                PixelSquare(primary, transition, delay = 1200)
                // Bottom edge
                PixelSquare(tertiary, transition, delay = 1050)
                // Bottom-right corner
                PixelSquare(primary, transition, delay = 1350)
            }
        }
    }
}

@Composable
internal fun PixelSquare(
    color: androidx.compose.ui.graphics.Color,
    transition: InfiniteTransition,
    delay: Int,
    breathing: Boolean = false,
) {
    val alpha by transition.animateFloat(
        initialValue = if (breathing) 0.3f else 0.1f,
        targetValue = if (breathing) 1f else 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (breathing) 2000 else 800,
                delayMillis = delay,
                easing = if (breathing) FastOutSlowInEasing else LinearEasing,
            ),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pixel-$delay",
    )
    
    Box(
        Modifier
            .size(3.dp)
            .background(color.copy(alpha = alpha)),
    )
}

@Composable
private fun EmptySessions(archived: Boolean, onNew: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = if (archived) "No archived sessions" else "No sessions yet",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!archived) {
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onNew) { Text("Create session") }
        }
    }
}

@Composable
private fun RenameDialog(session: SessionInfo, onDismiss: () -> Unit, onRename: (String) -> Unit) {
    var title by remember(session.id) { mutableStateOf(session.title.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename session") },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onRename(title.trim()) }, enabled = title.isNotBlank()) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// --- Grouping ---

private fun groupByDate(sessions: List<SessionInfo>): List<Pair<String, List<SessionInfo>>> {
    val order = listOf("Today", "Yesterday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday", "Older")
    val groups = linkedMapOf<String, MutableList<SessionInfo>>()

    for (session in sessions.sortedByDescending { it.time.updated }) {
        val label = dateGroupLabel(session.time.updated)
        groups.getOrPut(label) { mutableListOf() }.add(session)
    }

    return order.flatMap { key ->
        groups[key]?.let { listOf(key to it) } ?: emptyList()
    }
}

private fun groupByProject(sessions: List<SessionInfo>): List<Pair<String, List<SessionInfo>>> {
    return sessions
        .groupBy { it.directory.ifBlank { "Server" } }
        .toList()
}

private fun dateGroupLabel(millis: Long): String {
    if (millis <= 0) return "Older"
    val cal = java.util.Calendar.getInstance()
    cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
    cal.set(java.util.Calendar.MINUTE, 0)
    cal.set(java.util.Calendar.SECOND, 0)
    cal.set(java.util.Calendar.MILLISECOND, 0)
    val todayStart = cal.timeInMillis
    val yesterdayStart = todayStart - 86_400_000
    val weekStart = todayStart - 7L * 86_400_000

    return when {
        millis >= todayStart -> "Today"
        millis >= yesterdayStart -> "Yesterday"
        millis >= weekStart -> {
            cal.timeInMillis = millis
            java.text.SimpleDateFormat("EEEE", java.util.Locale.getDefault()).format(cal.time)
        }
        else -> "Older"
    }
}
