package com.tom7.openremote.ui.sessions

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tom7.openremote.AppViewModel
import com.tom7.openremote.RealtimeStatus
import com.tom7.openremote.data.OpenCodeApi
import com.tom7.openremote.data.SessionInfo
import com.tom7.openremote.data.ThemeMode
import com.tom7.openremote.ui.common.relativeTime
import com.tom7.openremote.ui.common.sessionSubtitle
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

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
    val realtime by app.realtime.collectAsStateWithLifecycle()
    val themeMode by app.themeMode.collectAsStateWithLifecycle()
    val dark = when (themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("Sessions", style = MaterialTheme.typography.titleLarge)
                            Text(
                                text = serverHost(api.baseUrl),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Box(
                            Modifier
                                .size(7.dp)
                                .background(
                                    color = when (realtime) {
                                        RealtimeStatus.Live -> MaterialTheme.colorScheme.tertiary
                                        RealtimeStatus.Connecting -> MaterialTheme.colorScheme.primary
                                        RealtimeStatus.Offline -> MaterialTheme.colorScheme.error
                                    },
                                    shape = CircleShape,
                                ),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { searchOpen = !searchOpen }) {
                        Icon(
                            if (searchOpen) Icons.Outlined.Close else Icons.Outlined.Search,
                            contentDescription = "Search sessions",
                        )
                    }
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = { app.setTheme(if (dark) ThemeMode.Light else ThemeMode.Dark) }) {
                        Icon(
                            if (dark) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                            contentDescription = if (dark) "Switch to light theme" else "Switch to dark theme",
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showNewSession = true },
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text("New session") },
                shape = RoundedCornerShape(16.dp),
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary,
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (searchOpen) {
                OutlinedTextField(
                    value = state.search,
                    onValueChange = viewModel::setSearch,
                    placeholder = { Text("Search sessions") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    trailingIcon = {
                        if (state.search.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setSearch("") }) {
                                Icon(Icons.Outlined.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(14.dp),
                )
            }

            FlowRow(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilterChip(
                    selected = viewMode == SessionsViewMode.ByDate,
                    onClick = { viewMode = SessionsViewMode.ByDate },
                    label = { Text("By date") },
                )
                FilterChip(
                    selected = viewMode == SessionsViewMode.ByProject,
                    onClick = { viewMode = SessionsViewMode.ByProject },
                    label = { Text("By project") },
                )
                FilterChip(
                    selected = !state.archived,
                    onClick = { viewModel.setArchived(false) },
                    label = { Text("Active") },
                )
                FilterChip(
                    selected = state.archived,
                    onClick = { viewModel.setArchived(true) },
                    label = { Text("Archived") },
                )
                Spacer(Modifier.weight(1f))
                if (state.loading || state.refreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp).align(Alignment.CenterVertically),
                        strokeWidth = 2.dp,
                    )
                }
            }

            state.error?.let { error ->
                Card(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { viewModel.dismissError(); viewModel.refresh() }) {
                            Text("Retry", color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
            }

            PullToRefreshBox(
                isRefreshing = state.refreshing && !state.loading,
                onRefresh = { viewModel.refresh() },
                modifier = Modifier.fillMaxSize(),
            ) {
                val listState = rememberLazyListState()
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 6.dp,
                        bottom = 96.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (state.loading && state.sessions.isEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(top = 80.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
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
                                path = if (viewMode == SessionsViewMode.ByProject) sessions.firstOrNull()?.directory else null,
                                icon = if (viewMode == SessionsViewMode.ByDate) Icons.Outlined.History else Icons.Outlined.Folder,
                                onClick = {
                                    expandedGroups = if (expanded) expandedGroups - label else expandedGroups + label
                                },
                                action = if (label == "Today" && viewMode == SessionsViewMode.ByDate) {
                                    {
                                        IconButton(
                                            onClick = { showNewSession = true },
                                            modifier = Modifier.size(28.dp),
                                        ) {
                                            Icon(
                                                Icons.Outlined.Add,
                                                contentDescription = "New session",
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
                                    }
                                } else null,
                            )
                        }
                        if (expanded) {
                            items(sessions, key = { it.id }) { session ->
                                SessionRow(
                                    session = session,
                                    busy = session.id in state.busy,
                                    showDirectory = viewMode == SessionsViewMode.ByDate,
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
                    if (state.hasMore) {
                        item {
                            TextButton(
                                onClick = { viewModel.loadMore() },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                if (state.refreshing) {
                                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Text("Load more")
                                }
                            }
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
            text = { Text("\"${session.displayTitle}\" and its history will be permanently deleted.") },
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
    path: String?,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    action: @Composable (() -> Unit)? = null,
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 0f else -90f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "chevronRotation"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                Icons.Outlined.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
                modifier = Modifier
                    .size(18.dp)
                    .rotate(rotation),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
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
            action?.invoke()
            Spacer(Modifier.width(8.dp))
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
        }
        if (path != null && path.isNotBlank()) {
            Text(
                text = truncatedPath(path),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.padding(start = 32.dp, top = 2.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SessionRow(
    session: SessionInfo,
    busy: Boolean,
    showDirectory: Boolean,
    menuOpen: Boolean,
    onOpen: () -> Unit,
    onMenu: () -> Unit,
    onDismissMenu: () -> Unit,
    onRename: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SessionAvatar(avatarText(session), Modifier.size(30.dp))
            if (busy) {
                Spacer(Modifier.width(6.dp))
                RunningIndicator()
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = session.displayTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (showDirectory && session.directory.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = folderName(session.directory),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = buildString {
                        append(sessionSubtitle(session))
                        if (busy) append(" · working")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (busy) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box {
                IconButton(onClick = onMenu) {
                    Icon(
                        Icons.Outlined.MoreVert,
                        contentDescription = "Session actions",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
}

@Composable
private fun SessionAvatar(text: String, modifier: Modifier = Modifier) {
    val letter = text.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    val color = remember(text) { avatarColor(text) }
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = letter,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun RunningIndicator(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "running")
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { i ->
            val alpha by transition.animateFloat(
                initialValue = 0.2f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(500, delayMillis = i * 150),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pixel$i"
            )
            Box(
                Modifier
                    .size(3.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha))
            )
        }
    }
}

@Composable
private fun EmptySessions(archived: Boolean, onNew: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = if (archived) "No archived sessions" else "No sessions yet",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (archived) "Archived sessions stay here until you restore or delete them."
            else "Start a session and OpenCode will work in your project on the computer.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!archived) {
            Spacer(Modifier.height(14.dp))
            TextButton(onClick = onNew) { Text("Create your first session") }
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
    val cal = Calendar.getInstance()
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    val todayStart = cal.timeInMillis
    val yesterdayStart = todayStart - 86_400_000
    val weekStart = todayStart - 7L * 86_400_000

    return when {
        millis >= todayStart -> "Today"
        millis >= yesterdayStart -> "Yesterday"
        millis >= weekStart -> {
            cal.timeInMillis = millis
            SimpleDateFormat("EEEE", Locale.getDefault()).format(cal.time)
        }
        else -> "Older"
    }
}

// --- Helpers ---

private fun folderName(path: String): String =
    path.trimEnd('/', '\\').substringAfterLast('/').substringAfterLast('\\').ifBlank { "Server" }

private fun truncatedPath(path: String, maxChars: Int = 32): String =
    if (path.length <= maxChars) path else "…${path.takeLast(maxChars - 1)}"

private fun avatarText(session: SessionInfo): String {
    val dir = session.directory
    val name = if (dir.isBlank()) session.displayTitle else folderName(dir)
    return name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
}

private fun avatarColor(text: String): Color {
    val hash = abs(text.hashCode())
    val palette = listOf(
        Color(0xFF4A7FC4),
        Color(0xFF6B52A3),
        Color(0xFF2D8A6E),
        Color(0xFFB07428),
        Color(0xFFB84848),
        Color(0xFF3A8FAF),
        Color(0xFF8560B0),
        Color(0xFF2FA08A),
    )
    return palette[hash % palette.size]
}

private fun serverHost(url: String): String = runCatching {
    val uri = URI(url)
    buildString {
        append(uri.host ?: url)
        if (uri.port > 0) append(":").append(uri.port)
    }
}.getOrDefault(url)
