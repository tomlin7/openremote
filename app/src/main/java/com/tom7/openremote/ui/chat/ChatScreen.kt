package com.tom7.openremote.ui.chat

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.CallSplit
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.automirrored.outlined.ViewSidebar
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tom7.openremote.AppViewModel
import com.tom7.openremote.RealtimeStatus
import com.tom7.openremote.data.FormInfo
import com.tom7.openremote.data.OpenCodeApi
import com.tom7.openremote.data.PermissionRequest
import com.tom7.openremote.ui.pickers.AgentPickerSheet
import com.tom7.openremote.ui.pickers.CommandPickerSheet
import com.tom7.openremote.ui.pickers.ModelPickerSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    api: OpenCodeApi,
    app: AppViewModel,
    sessionID: String,
    onBack: () -> Unit,
    onOpenSession: (String) -> Unit,
) {
    val viewModel: ChatViewModel = viewModel(
        key = "chat:$sessionID:${app.apiKey}",
        factory = viewModelFactory {
            initializer { ChatViewModel(api, sessionID, app.events) }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val realtime by app.realtime.collectAsStateWithLifecycle()
    val defaultDelivery by app.defaultDelivery.collectAsStateWithLifecycle()
    val showReasoning by app.showReasoning.collectAsStateWithLifecycle()

    val snackbar = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    var draft by rememberSaveable(sessionID) { mutableStateOf("") }
    var attachments by remember(sessionID) { mutableStateOf<List<OutgoingAttachment>>(emptyList()) }
    var deliveryOverride by rememberSaveable(sessionID) { mutableStateOf<String?>(null) }
    val delivery = deliveryOverride ?: defaultDelivery

    var showMenu by remember { mutableStateOf(false) }
    var showModelPicker by remember { mutableStateOf(false) }
    var showAgentPicker by remember { mutableStateOf(false) }
    var showCommandPicker by remember { mutableStateOf(false) }
    var showPanels by remember { mutableStateOf(false) }
    var renameOpen by remember { mutableStateOf(false) }
    var deleteOpen by remember { mutableStateOf(false) }
    var archiveOpen by remember { mutableStateOf(false) }
    var shellOpen by remember { mutableStateOf(false) }
    var openForm by remember { mutableStateOf<FormInfo?>(null) }

    LaunchedEffect(state.toast) {
        state.toast?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeToast()
        }
    }

    LaunchedEffect(state.closed) {
        if (state.closed) onBack()
    }

    val itemCount = remember(state.messages.size, state.pending.size, state.queued.size, state.permissions.size, state.forms.size, state.hasOlder, state.loadingOlder) {
        state.messages.size + state.pending.size + state.queued.size + state.permissions.size + state.forms.size +
            (if (state.hasOlder || state.loadingOlder) 1 else 0)
    }

    var stickToBottom by remember { mutableStateOf(true) }
    var initialScrollDone by remember { mutableStateOf(false) }

    // Follow the conversation unless the reader has scrolled back to older messages.
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: -1) to info.totalItemsCount
        }.collect { (lastVisible, total) ->
            if (lastVisible >= 0 && total > 0) stickToBottom = lastVisible >= total - 2
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { index ->
                if (index <= 1) viewModel.loadOlder()
            }
    }

    LaunchedEffect(itemCount) {
        if (itemCount == 0) return@LaunchedEffect
        if (!initialScrollDone) {
            listState.scrollToItem(itemCount - 1)
            initialScrollDone = true
            return@LaunchedEffect
        }
        if (stickToBottom || state.pending.isNotEmpty()) {
            listState.animateScrollToItem(itemCount - 1)
        }
    }

    if (state.fatalError != null) {
        FatalError(
            message = state.fatalError.orEmpty(),
            onRetry = { viewModel.retryLoad() },
            onBack = onBack,
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Column {
                        Text(
                            text = state.session?.displayTitle ?: "Session",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(5.dp)
                                    .background(
                                        color = when (realtime) {
                                            RealtimeStatus.Live -> MaterialTheme.colorScheme.tertiary
                                            RealtimeStatus.Connecting -> MaterialTheme.colorScheme.primary
                                            RealtimeStatus.Offline -> MaterialTheme.colorScheme.error
                                        },
                                        shape = CircleShape,
                                    ),
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = listOfNotNull(
                                    state.selectedAgent,
                                    state.selectedModel?.id,
                                ).joinToString(" · ").ifBlank { serverHost(api.baseUrl) },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showPanels = true }) {
                        Icon(Icons.AutoMirrored.Outlined.ViewSidebar, contentDescription = "Session panels")
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = "Session actions")
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Rename") },
                                onClick = { showMenu = false; renameOpen = true },
                                leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                            )
                            DropdownMenuItem(
                                text = { Text("Fork session") },
                                onClick = {
                                    showMenu = false
                                    viewModel.fork { session -> onOpenSession(session.id) }
                                },
                                leadingIcon = { Icon(Icons.AutoMirrored.Outlined.CallSplit, contentDescription = null) },
                            )
                            DropdownMenuItem(
                                text = { Text("Compact context") },
                                onClick = { showMenu = false; viewModel.compact() },
                                leadingIcon = { Icon(Icons.Outlined.Compress, contentDescription = null) },
                            )
                            DropdownMenuItem(
                                text = { Text("Run shell command") },
                                onClick = { showMenu = false; shellOpen = true },
                                leadingIcon = { Icon(Icons.Outlined.Terminal, contentDescription = null) },
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Archive") },
                                onClick = { showMenu = false; archiveOpen = true },
                                leadingIcon = { Icon(Icons.Outlined.Archive, contentDescription = null) },
                            )
                            DropdownMenuItem(
                                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                onClick = { showMenu = false; deleteOpen = true },
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
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    StatusStrip(
                        busy = state.busy,
                        retry = state.status?.takeIf { it.type == "retry" },
                    )
                    ChatComposer(
                        draft = draft,
                        onDraftChange = { draft = it },
                        attachments = attachments,
                        onAttachmentsChange = { attachments = it },
                        busy = state.busy,
                        sending = state.sending,
                        delivery = delivery,
                        onDeliveryChange = { deliveryOverride = it },
                        model = state.selectedModel,
                        models = state.models,
                        agent = state.selectedAgent,
                        agents = state.agents,
                        commands = state.commands,
                        onPickModel = { showModelPicker = true },
                        onPickAgent = { showAgentPicker = true },
                        onPickCommand = { showCommandPicker = true },
                        onSend = { text, files, chosenDelivery ->
                            viewModel.send(text, files, chosenDelivery)
                            deliveryOverride = null
                        },
                        onStop = { viewModel.interrupt() },
                        onError = { viewModel.reportError(it) },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 14.dp,
                    end = 14.dp,
                    top = 8.dp,
                    bottom = 12.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (state.hasOlder || state.loadingOlder) {
                    item(key = "older") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            if (state.loadingOlder) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                TextButton(onClick = { viewModel.loadOlder() }) {
                                    Text("Load earlier messages")
                                }
                            }
                        }
                    }
                }

                if (state.loading && state.messages.isEmpty()) {
                    item(key = "loading") {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 64.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                } else if (state.messages.isEmpty() && state.pending.isEmpty()) {
                    item(key = "empty") {
                        EmptyChat()
                    }
                }

                items(state.messages, key = { it.id }) { message ->
                    TimelineMessage(
                        message = message,
                        busy = state.busy,
                        showReasoning = showReasoning,
                        onOpenSession = onOpenSession,
                    )
                }

                items(state.pending, key = { it.id }) { message ->
                    UserMessage(message, pending = true)
                }

                items(state.queued, key = { "queued:" + it.id }) { message ->
                    UserMessage(
                        message = message,
                        pending = true,
                        onSteer = { viewModel.steerQueued(message.id) },
                        onCancel = { viewModel.cancelQueued(message.id) },
                    )
                }

                items(state.permissions, key = { "permission:" + it.id }) { request ->
                    PermissionCard(request = request, onReply = { decision ->
                        viewModel.replyPermission(request, decision)
                    })
                }

                items(state.forms, key = { "form:" + it.id }) { form ->
                    FormCard(form = form, onOpen = { openForm = form })
                }
            }
        }
    }

    if (showModelPicker) {
        ModelPickerSheet(
            models = state.models,
            providers = state.providers,
            selected = state.selectedModel,
            onDismiss = { showModelPicker = false },
            onSelect = { model ->
                showModelPicker = false
                viewModel.switchModel(model)
            },
        )
    }

    if (showAgentPicker) {
        AgentPickerSheet(
            agents = state.agents,
            selected = state.selectedAgent,
            onDismiss = { showAgentPicker = false },
            onSelect = { agent ->
                showAgentPicker = false
                viewModel.switchAgent(agent)
            },
        )
    }

    if (showCommandPicker) {
        CommandPickerSheet(
            commands = state.commands,
            onDismiss = { showCommandPicker = false },
            onSelect = { command ->
                showCommandPicker = false
                draft = "/${command.name} "
            },
        )
    }

    openForm?.let { form ->
        FormSheet(
            form = form,
            onDismiss = { openForm = null },
            onSubmit = { answer ->
                viewModel.replyForm(form, answer)
                openForm = null
            },
            onCancel = {
                viewModel.cancelForm(form)
                openForm = null
            },
        )
    }

    if (renameOpen) {
        val current = state.session?.title.orEmpty()
        var title by remember(state.session?.title) { mutableStateOf(current) }
        AlertDialog(
            onDismissRequest = { renameOpen = false },
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
                TextButton(
                    onClick = {
                        viewModel.rename(title.trim())
                        renameOpen = false
                    },
                    enabled = title.isNotBlank(),
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renameOpen = false }) { Text("Cancel") } },
        )
    }

    if (shellOpen) {
        var command by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { shellOpen = false },
            title = { Text("Run shell command") },
            text = {
                Column {
                    Text(
                        "Runs in the session directory on your computer.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = command,
                        onValueChange = { command = it },
                        label = { Text("Command") },
                        singleLine = true,
                        placeholder = { Text("git status") },
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.runShell(command.trim())
                        shellOpen = false
                    },
                    enabled = command.isNotBlank(),
                ) { Text("Run") }
            },
            dismissButton = { TextButton(onClick = { shellOpen = false }) { Text("Cancel") } },
        )
    }

    if (archiveOpen) {
        AlertDialog(
            onDismissRequest = { archiveOpen = false },
            title = { Text("Archive session?") },
            text = { Text("You can restore it later from the archived list.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        archiveOpen = false
                        viewModel.setArchived(true) { onBack() }
                    },
                ) { Text("Archive") }
            },
            dismissButton = { TextButton(onClick = { archiveOpen = false }) { Text("Cancel") } },
        )
    }

    if (deleteOpen) {
        AlertDialog(
            onDismissRequest = { deleteOpen = false },
            title = { Text("Delete session?") },
            text = { Text("This removes the session and its history permanently.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteOpen = false
                        viewModel.delete { onBack() }
                    },
                ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteOpen = false }) { Text("Cancel") } },
        )
    }

    if (showPanels) {
        SessionPanelsSheet(
            messages = state.messages,
            session = state.session,
            api = api,
            onDismiss = { showPanels = false },
        )
    }
}

@Composable
private fun StatusStrip(busy: Boolean, retry: com.tom7.openremote.data.SessionStatus?) {
    when {
        retry != null -> {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(Modifier.size(13.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Retrying (attempt ${retry.attempt ?: 1}) · ${retry.message.orEmpty()}",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        busy -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(7.dp))
                Text(
                    "OpenCode is working…",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EmptyChat() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 56.dp, start = 24.dp, end = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Ready when you are",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Ask OpenCode to explain code, make changes, or run a command. It works inside your project on the computer.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun FatalError(message: String, onRetry: () -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Could not open this session", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TextButton(onClick = onBack) { Text("Back") }
            TextButton(onClick = onRetry) { Text("Retry") }
        }
    }
}

private fun serverHost(url: String): String = runCatching {
    val uri = java.net.URI(url)
    buildString {
        append(uri.host ?: url)
        if (uri.port > 0) append(":").append(uri.port)
    }
}.getOrDefault(url)
