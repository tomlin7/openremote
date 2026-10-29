package com.tom7.openremote.ui.sessions

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
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
import androidx.compose.ui.graphics.Color
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

    LaunchedEffect(state.toast) {
        state.toast?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeToast()
        }
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

            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
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
                val grouped = remember(state.sessions) {
                    state.sessions.groupBy { it.directory }.toList()
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 6.dp,
                        bottom = 96.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
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
                    grouped.forEach { (directory, sessions) ->
                        item(key = "group:$directory") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp, bottom = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Outlined.Folder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(Modifier.width(7.dp))
                                Text(
                                    text = directory.ifBlank { "Server" },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = sessions.size.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                )
                            }
                        }
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
    val statusColor = when {
        busy -> MaterialTheme.colorScheme.primary
        session.outcome == "failed" -> MaterialTheme.colorScheme.error
        session.outcome == "interrupted" -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.65f)
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .background(statusColor, CircleShape),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = session.displayTitle,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
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

private fun serverHost(url: String): String = runCatching {
    val uri = URI(url)
    buildString {
        append(uri.host ?: url)
        if (uri.port > 0) append(":").append(uri.port)
    }
}.getOrDefault(url)
