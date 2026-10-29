package com.tom7.openremote.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tom7.openremote.data.AppJson
import com.tom7.openremote.data.AgentInfo
import com.tom7.openremote.data.CommandInfo
import com.tom7.openremote.data.FormInfo
import com.tom7.openremote.data.MessagePage
import com.tom7.openremote.data.ModelInfo
import com.tom7.openremote.data.ModelRef
import com.tom7.openremote.data.OpenCodeApi
import com.tom7.openremote.data.PermissionRequest
import com.tom7.openremote.data.PromptFile
import com.tom7.openremote.data.ProviderInfo
import com.tom7.openremote.data.ServerEvent
import com.tom7.openremote.data.SessionInfo
import com.tom7.openremote.data.SessionMessage
import com.tom7.openremote.data.SessionStatus
import com.tom7.openremote.data.userMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

data class OutgoingAttachment(
    val uri: String,
    val name: String,
    val mime: String,
    val base64: String,
)

data class ChatUiState(
    val session: SessionInfo? = null,
    val messages: List<SessionMessage> = emptyList(),
    val pending: List<SessionMessage> = emptyList(),
    val queued: List<SessionMessage> = emptyList(),
    val loading: Boolean = true,
    val loadingOlder: Boolean = false,
    val hasOlder: Boolean = false,
    val busy: Boolean = false,
    val status: SessionStatus? = null,
    val permissions: List<PermissionRequest> = emptyList(),
    val forms: List<FormInfo> = emptyList(),
    val models: List<ModelInfo> = emptyList(),
    val providers: List<ProviderInfo> = emptyList(),
    val agents: List<AgentInfo> = emptyList(),
    val commands: List<CommandInfo> = emptyList(),
    val selectedAgent: String? = null,
    val selectedModel: ModelRef? = null,
    val sending: Boolean = false,
    val toast: String? = null,
    val fatalError: String? = null,
    val closed: Boolean = false,
)

class ChatViewModel(
    private val api: OpenCodeApi,
    private val sessionID: String,
    events: SharedFlow<ServerEvent>,
) : ViewModel() {
    private val pageSize = 30
    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private var olderMessages: List<SessionMessage> = emptyList()
    private var tailMessages: List<SessionMessage> = emptyList()
    private var olderCursor: String? = null

    private var messageJob: Job? = null
    private var olderJob: Job? = null
    private var eventRefreshJob: Job? = null
    private var catalogJob: Job? = null
    private var messagesQueued = false
    private var initialQueued = false

    init {
        loadAll()
        viewModelScope.launch {
            _state.map { it.busy }.distinctUntilChanged().collectLatest { busy ->
                var idleChecks = 0
                while (busy && isActive) {
                    delay(1_200)
                    if (_state.value.loading) continue
                    // Ephemeral status/execution events can be missed across a reconnect.
                    // session.active is the durable answer, so reconcile against it periodically.
                    if (idleChecks++ % 2 == 0) {
                        val active = runCatching { api.activeSessions() }.getOrNull()
                        if (active != null && sessionID !in active) {
                            _state.value = _state.value.copy(
                                busy = false,
                                status = SessionStatus(type = "idle"),
                            )
                            refreshMessages()
                            refreshPending()
                            refreshSession()
                            return@collectLatest
                        }
                    }
                    refreshMessages()
                }
            }
        }
        viewModelScope.launch {
            events.collect { event -> handleEvent(event) }
        }
    }

    fun refresh() {
        refreshSession()
        refreshMessages()
        refreshPending()
    }

    fun retryLoad() {
        if (_state.value.loading) return
        _state.value = _state.value.copy(loading = true, fatalError = null)
        messageJob?.cancel()
        messagesQueued = false
        initialQueued = false
        tailMessages = emptyList()
        olderMessages = emptyList()
        olderCursor = null
        loadAll()
    }

    private fun loadAll() {
        viewModelScope.launch {
            try {
                val session = api.session(sessionID)
                _state.value = _state.value.copy(
                    session = session,
                    selectedAgent = session.agent,
                    selectedModel = session.model,
                )
                runCatching { api.activeSessions() }.getOrNull()?.let { active ->
                    _state.value = _state.value.copy(busy = sessionID in active)
                }
                session.time.idle?.let { idle ->
                    if ((session.time.viewed ?: 0) < idle) {
                        runCatching { api.markViewed(sessionID, idle) }
                    }
                }
            } catch (error: Throwable) {
                _state.value = _state.value.copy(
                    loading = false,
                    fatalError = error.userMessage(),
                )
                return@launch
            }
            refreshMessages(initial = true)
            refreshPending()
            loadCatalog()
        }
    }

    private fun loadCatalog() {
        catalogJob?.cancel()
        catalogJob = viewModelScope.launch {
            runCatching { api.models() }.onSuccess { models ->
                _state.value = _state.value.copy(
                    models = models.filter { it.enabled && it.status != "deprecated" },
                )
            }
            runCatching { api.providers() }.onSuccess { providers ->
                _state.value = _state.value.copy(providers = providers)
            }
            runCatching { api.agents() }.onSuccess { agents ->
                _state.value = _state.value.copy(agents = agents.filter { !it.hidden })
            }
            runCatching { api.commands() }.onSuccess { commands ->
                _state.value = _state.value.copy(commands = commands)
            }
        }
    }

    private fun refreshSession() {
        viewModelScope.launch {
            runCatching { api.session(sessionID) }.onSuccess { session ->
                _state.value = _state.value.copy(
                    session = session,
                    selectedAgent = session.agent ?: _state.value.selectedAgent,
                    selectedModel = session.model ?: _state.value.selectedModel,
                )
            }
        }
    }

    // Optimistic busy flags are corrected from the server's durable inventory.
    private fun reconcileBusy() {
        viewModelScope.launch {
            val active = runCatching { api.activeSessions() }.getOrNull()
            if (active == null) return@launch
            val running = sessionID in active
            _state.value = _state.value.copy(
                busy = running,
                status = if (running) _state.value.status else SessionStatus(type = "idle"),
            )
        }
    }

    private fun refreshMessages(initial: Boolean = false) {
        if (initial) initialQueued = true
        // Coalesce instead of cancelling: a slow request must finish before the next
        // refresh starts, otherwise a busy stream of events keeps the timeline empty.
        if (messageJob?.isActive == true) {
            messagesQueued = true
            return
        }
        messageJob = viewModelScope.launch {
            do {
                messagesQueued = false
                val first = initialQueued
                initialQueued = false
                try {
                    val page: MessagePage = api.messages(sessionID, limit = pageSize, order = "desc")
                    tailMessages = page.data.reversed()
                    if (first) {
                        olderCursor = page.cursor.next
                        _state.value = _state.value.copy(
                            hasOlder = page.data.size >= pageSize && olderCursor != null,
                        )
                    }
                    publishMessages()
                } catch (error: Throwable) {
                    if (first) {
                        _state.value = _state.value.copy(toast = error.userMessage())
                    }
                } finally {
                    if (first) {
                        _state.value = _state.value.copy(loading = false)
                    }
                }
            } while (messagesQueued)
        }
    }

    private fun publishMessages() {
        val olderIDs = olderMessages.map { it.id }.toSet()
        val merged = olderMessages + tailMessages.filter { it.id !in olderIDs }
        val texts = tailMessages.filter { it.type == "user" }.mapNotNull { it.text }.toSet()
        _state.value = _state.value.copy(
            messages = merged,
            pending = _state.value.pending.filterNot { it.text in texts },
        )
    }

    fun loadOlder() {
        val cursor = olderCursor ?: return
        if (_state.value.loadingOlder || olderJob?.isActive == true) return
        olderJob = viewModelScope.launch {
            _state.value = _state.value.copy(loadingOlder = true)
            try {
                val page: MessagePage = api.messages(sessionID, limit = pageSize, cursor = cursor)
                val next = page.data.reversed()
                val known = (olderMessages + tailMessages).map { it.id }.toSet()
                olderMessages = (next.filter { it.id !in known } + olderMessages).distinctBy { it.id }
                olderCursor = page.cursor.next
                publishMessages()
                _state.value = _state.value.copy(
                    loadingOlder = false,
                    hasOlder = page.data.size >= pageSize && olderCursor != null,
                )
            } catch (error: Throwable) {
                _state.value = _state.value.copy(loadingOlder = false, toast = error.userMessage())
            }
        }
    }

    private fun refreshPending() {
        viewModelScope.launch {
            val permissions = runCatching { api.permissions(sessionID) }.getOrNull()
            val forms = runCatching { api.forms(sessionID) }.getOrNull()
            val inbox = runCatching { api.inbox(sessionID) }.getOrNull()
            _state.value = _state.value.copy(
                permissions = permissions ?: _state.value.permissions,
                forms = forms?.filter { it.fields.isNotEmpty() } ?: _state.value.forms,
                queued = inbox?.filter { it.type == "user" }?.map { it.toMessage() } ?: _state.value.queued,
                pending = inbox?.let { items ->
                    _state.value.pending.filterNot { local ->
                        items.any { item ->
                            item.type == "user" &&
                                item.payload.text == local.text.orEmpty() &&
                                item.payload.files.size == local.files.size
                        }
                    }
                } ?: _state.value.pending,
            )
        }
    }

    fun steerQueued(inboxID: String) {
        viewModelScope.launch {
            try {
                api.steerInbox(sessionID, inboxID)
                refreshPending()
            } catch (error: Throwable) {
                _state.value = _state.value.copy(toast = error.userMessage())
            }
        }
    }

    fun cancelQueued(inboxID: String) {
        viewModelScope.launch {
            try {
                api.cancelInbox(sessionID, inboxID)
                refreshPending()
            } catch (error: Throwable) {
                _state.value = _state.value.copy(toast = error.userMessage())
            }
        }
    }

    fun send(text: String, attachments: List<OutgoingAttachment>, delivery: String? = null) {
        val body = text.trim()
        if (body.isEmpty() && attachments.isEmpty()) return
        if (_state.value.sending) return

        val files = attachments.map { attachment ->
            // Convert base64 data to a data URI
            val uri = "data:${attachment.mime};base64,${attachment.base64}"
            PromptFile(
                uri = uri,
                name = attachment.name,
            )
        }

        val command = commandFor(body)
        val optimistic = SessionMessage(
            id = "local_${System.nanoTime()}",
            type = "user",
            time = com.tom7.openremote.data.MessageTime(created = System.currentTimeMillis()),
            text = body,
            files = files,
            metadata = JsonObject(mapOf("displayText" to JsonPrimitive(body))),
            delivery = delivery,
        )

        _state.value = _state.value.copy(
            sending = true,
            pending = _state.value.pending + optimistic,
            busy = true,
        )

        viewModelScope.launch {
            try {
                if (command != null) {
                    api.runCommand(
                        sessionID = sessionID,
                        name = command.first,
                        text = command.second,
                        files = files,
                        delivery = delivery,
                    )
                } else {
                    api.sendPrompt(
                        sessionID = sessionID,
                        text = body,
                        files = files,
                        delivery = delivery,
                    )
                }
                _state.value = _state.value.copy(sending = false, toast = null)
                delay(180)
                refreshMessages()
                refreshPending()
                refreshSession()
            } catch (error: Throwable) {
                _state.value = _state.value.copy(
                    sending = false,
                    pending = _state.value.pending.filterNot { it.id == optimistic.id },
                    toast = error.userMessage(),
                )
                reconcileBusy()
            }
        }
    }

    private fun commandFor(text: String): Pair<String, String>? {
        if (!text.startsWith("/")) return null
        val name = text.drop(1).substringBefore(' ').trim()
        if (name.isEmpty()) return null
        if (_state.value.commands.none { it.name == name }) return null
        return name to text.substringAfter(' ', "").trim()
    }

    fun switchModel(model: ModelRef) {
        _state.value = _state.value.copy(selectedModel = model)
        viewModelScope.launch {
            try {
                api.switchModel(sessionID, model)
                refreshSession()
            } catch (error: Throwable) {
                _state.value = _state.value.copy(toast = error.userMessage())
            }
        }
    }

    fun switchAgent(agent: String) {
        _state.value = _state.value.copy(selectedAgent = agent)
        viewModelScope.launch {
            try {
                api.switchAgent(sessionID, agent)
                refreshSession()
            } catch (error: Throwable) {
                _state.value = _state.value.copy(toast = error.userMessage())
            }
        }
    }

    fun interrupt() {
        viewModelScope.launch {
            try {
                api.interrupt(sessionID)
                _state.value = _state.value.copy(busy = false)
                delay(200)
                refresh()
            } catch (error: Throwable) {
                _state.value = _state.value.copy(toast = error.userMessage())
            }
        }
    }

    fun compact() {
        viewModelScope.launch {
            try {
                api.compact(sessionID)
                _state.value = _state.value.copy(toast = "Compaction requested", busy = true)
            } catch (error: Throwable) {
                _state.value = _state.value.copy(toast = error.userMessage())
                reconcileBusy()
            }
        }
    }

    fun rename(title: String) {
        viewModelScope.launch {
            try {
                api.renameSession(sessionID, title)
                refreshSession()
                _state.value = _state.value.copy(toast = "Session renamed")
            } catch (error: Throwable) {
                _state.value = _state.value.copy(toast = error.userMessage())
            }
        }
    }

    fun setArchived(archived: Boolean, onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                api.archiveSession(sessionID, archived)
                onDone()
            } catch (error: Throwable) {
                _state.value = _state.value.copy(toast = error.userMessage())
            }
        }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                api.deleteSession(sessionID)
                onDone()
            } catch (error: Throwable) {
                _state.value = _state.value.copy(toast = error.userMessage())
            }
        }
    }

    fun fork(onCreated: (SessionInfo) -> Unit) {
        viewModelScope.launch {
            try {
                val session = api.forkSession(sessionID)
                onCreated(session)
            } catch (error: Throwable) {
                _state.value = _state.value.copy(toast = error.userMessage())
            }
        }
    }

    fun runShell(command: String) {
        if (command.isBlank()) return
        viewModelScope.launch {
            try {
                api.runShell(sessionID, command)
                _state.value = _state.value.copy(busy = true, toast = "Shell command started")
                delay(300)
                refreshMessages()
            } catch (error: Throwable) {
                _state.value = _state.value.copy(toast = error.userMessage())
                reconcileBusy()
            }
        }
    }

    fun replyPermission(request: PermissionRequest, decision: String) {
        viewModelScope.launch {
            try {
                api.replyPermission(sessionID, request.id, decision)
                _state.value = _state.value.copy(
                    permissions = _state.value.permissions.filterNot { it.id == request.id },
                )
            } catch (error: Throwable) {
                _state.value = _state.value.copy(toast = error.userMessage())
                refreshPending()
            }
        }
    }

    fun replyForm(form: FormInfo, answer: Map<String, JsonElement>) {
        viewModelScope.launch {
            try {
                api.replyForm(sessionID, form.id, answer)
                _state.value = _state.value.copy(
                    forms = _state.value.forms.filterNot { it.id == form.id },
                    toast = "Answer sent",
                )
            } catch (error: Throwable) {
                _state.value = _state.value.copy(toast = error.userMessage())
            }
        }
    }

    fun cancelForm(form: FormInfo) {
        viewModelScope.launch {
            try {
                api.cancelForm(sessionID, form.id)
                _state.value = _state.value.copy(
                    forms = _state.value.forms.filterNot { it.id == form.id },
                )
            } catch (error: Throwable) {
                _state.value = _state.value.copy(toast = error.userMessage())
            }
        }
    }

    fun reportError(message: String) {
        _state.value = _state.value.copy(toast = message)
    }

    fun consumeToast() {
        if (_state.value.toast != null) _state.value = _state.value.copy(toast = null)
    }

    private fun handleEvent(event: ServerEvent) {
        val target = event.sessionID
        if (target != null && target != sessionID) return

        when (event.type) {
            // The first event on every (re)connect. Ephemeral events we missed while
            // disconnected are gone, so re-read durable state and the active inventory.
            "server.connected" -> {
                viewModelScope.launch {
                    runCatching { api.activeSessions() }.getOrNull()?.let { active ->
                        val running = sessionID in active
                        _state.value = _state.value.copy(
                            busy = running,
                            status = if (running) _state.value.status else SessionStatus(type = "idle"),
                        )
                    }
                    scheduleMessagesRefresh()
                    refreshPending()
                    refreshSession()
                }
            }

            "session.status" -> {
                val status = event.obj("status")?.let {
                    runCatching { AppJson.decodeFromString<SessionStatus>(it.toString()) }.getOrNull()
                } ?: return
                val wasBusy = _state.value.busy
                _state.value = _state.value.copy(busy = status.busy, status = status)
                if (wasBusy != status.busy) {
                    if (!status.busy) {
                        scheduleMessagesRefresh()
                        refreshPending()
                        refreshSession()
                    }
                }
            }

            "session.execution.started" -> _state.value = _state.value.copy(busy = true)
            "session.execution.succeeded", "session.execution.failed",
            "session.execution.interrupted" -> {
                _state.value = _state.value.copy(busy = false, status = SessionStatus(type = "idle"))
                scheduleMessagesRefresh()
                refreshPending()
                refreshSession()
            }

            "session.idle" -> {
                _state.value = _state.value.copy(busy = false, status = SessionStatus(type = "idle"))
                scheduleMessagesRefresh()
            }

            "session.renamed" -> {
                val title = event.string("title")
                if (title != null) {
                    _state.value = _state.value.copy(
                        session = _state.value.session?.copy(title = title),
                    )
                }
            }

            "session.model.selected" -> {
                val model = event.obj("model")?.let {
                    runCatching { AppJson.decodeFromString<ModelRef>(it.toString()) }.getOrNull()
                }
                if (model != null) {
                    _state.value = _state.value.copy(selectedModel = model)
                }
            }

            "session.agent.selected" -> {
                event.string("agent")?.let { agent ->
                    _state.value = _state.value.copy(selectedAgent = agent)
                }
            }

            "session.deleted" -> _state.value = _state.value.copy(closed = true)

            "permission.asked", "permission.replied" -> refreshPending()
            "form.created", "form.replied", "form.cancelled" -> refreshPending()

            "session.inbox.delivered" -> {
                refreshPending()
                scheduleMessagesRefresh()
            }
            "session.inbox.enqueued", "session.inbox.cancelled",
            "session.inbox.delivery.changed" -> refreshPending()

            "session.text.ended", "session.reasoning.ended",
            "session.step.ended", "session.step.failed",
            "session.tool.called", "session.tool.success", "session.tool.failed",
            "session.retry.scheduled", "session.compaction.started",
            "session.compaction.ended", "session.compaction.failed",
            "session.synthetic", "session.shell.started", "session.shell.ended" -> scheduleMessagesRefresh()
        }
    }

    private fun scheduleMessagesRefresh() {
        eventRefreshJob?.cancel()
        eventRefreshJob = viewModelScope.launch {
            delay(180)
            refreshMessages()
        }
    }
}
