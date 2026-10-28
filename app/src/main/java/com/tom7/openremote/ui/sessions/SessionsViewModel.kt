package com.tom7.openremote.ui.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tom7.openremote.data.AgentInfo
import com.tom7.openremote.data.ModelRef
import com.tom7.openremote.data.OpenCodeApi
import com.tom7.openremote.data.ProjectInfo
import com.tom7.openremote.data.ServerEvent
import com.tom7.openremote.data.SessionInfo
import com.tom7.openremote.data.SessionStatus
import com.tom7.openremote.data.userMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonObject

data class SessionsUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val creating: Boolean = false,
    val sessions: List<SessionInfo> = emptyList(),
    val projects: List<ProjectInfo> = emptyList(),
    val defaultDirectory: String = "",
    val agents: List<AgentInfo> = emptyList(),
    val search: String = "",
    val archived: Boolean = false,
    val busy: Set<String> = emptySet(),
    val hasMore: Boolean = false,
    val error: String? = null,
    val toast: String? = null,
)

class SessionsViewModel(
    private val api: OpenCodeApi,
    events: kotlinx.coroutines.flow.SharedFlow<ServerEvent>,
) : ViewModel() {
    private val _state = MutableStateFlow(SessionsUiState())
    val state: StateFlow<SessionsUiState> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var searchJob: Job? = null
    private var refreshJob: Job? = null
    private var nextCursor: String? = null

    init {
        refresh(initial = true)
        viewModelScope.launch {
            events.collect { event -> handleEvent(event) }
        }
    }

    fun refresh(initial: Boolean = false) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.value = _state.value.copy(
                loading = initial && _state.value.sessions.isEmpty(),
                refreshing = !initial,
                error = null,
            )
            try {
                val page = api.sessions(
                    search = _state.value.search.takeIf { it.isNotBlank() },
                    archived = _state.value.archived,
                    limit = 100,
                )
                nextCursor = page.cursor.next
                val active = runCatching { api.activeSessions() }.getOrDefault(emptySet())
                _state.value = _state.value.copy(
                    loading = false,
                    refreshing = false,
                    sessions = page.data,
                    busy = active,
                    hasMore = !page.cursor.next.isNullOrBlank(),
                    error = null,
                )
            } catch (error: Throwable) {
                _state.value = _state.value.copy(
                    loading = false,
                    refreshing = false,
                    error = error.userMessage(),
                )
            }
        }

        if (_state.value.projects.isEmpty()) {
            viewModelScope.launch {
                runCatching { api.projects() }.onSuccess { projects ->
                    _state.value = _state.value.copy(projects = projects)
                }
                runCatching { api.location() }.onSuccess { location ->
                    _state.value = _state.value.copy(
                        defaultDirectory = location.project.canonical.ifBlank { location.directory },
                    )
                }
            }
            viewModelScope.launch {
                runCatching { api.agents() }.onSuccess { agents ->
                    _state.value = _state.value.copy(
                        agents = agents.filter { it.mode != "subagent" && !it.hidden },
                    )
                }
            }
        }
    }

    fun loadMore() {
        val cursor = nextCursor ?: return
        if (_state.value.refreshing || _state.value.loading) return
        viewModelScope.launch {
            _state.value = _state.value.copy(refreshing = true)
            try {
                val page = api.sessions(
                    search = _state.value.search.takeIf { it.isNotBlank() },
                    archived = _state.value.archived,
                    cursor = cursor,
                )
                nextCursor = page.cursor.next
                val existing = _state.value.sessions.map { it.id }.toSet()
                _state.value = _state.value.copy(
                    refreshing = false,
                    sessions = _state.value.sessions + page.data.filter { it.id !in existing },
                    hasMore = !page.cursor.next.isNullOrBlank(),
                )
            } catch (error: Throwable) {
                _state.value = _state.value.copy(refreshing = false, error = error.userMessage())
            }
        }
    }

    fun setSearch(value: String) {
        _state.value = _state.value.copy(search = value)
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(280)
            refresh()
        }
    }

    fun setArchived(archived: Boolean) {
        if (_state.value.archived == archived) return
        _state.value = _state.value.copy(archived = archived, sessions = emptyList(), loading = true)
        refresh(initial = true)
    }

    fun createSession(
        directory: String,
        title: String?,
        agent: String?,
        model: ModelRef?,
        onCreated: (SessionInfo) -> Unit,
    ) {
        viewModelScope.launch {
            _state.value = _state.value.copy(creating = true, error = null)
            try {
                val session = api.createSession(directory, title, agent, model)
                _state.value = _state.value.copy(creating = false)
                scheduleRefresh()
                onCreated(session)
            } catch (error: Throwable) {
                _state.value = _state.value.copy(creating = false, toast = error.userMessage())
            }
        }
    }

    fun rename(session: SessionInfo, title: String) {
        viewModelScope.launch {
            try {
                api.renameSession(session.id, title)
                scheduleRefresh()
            } catch (error: Throwable) {
                _state.value = _state.value.copy(toast = error.userMessage())
            }
        }
    }

    fun archive(session: SessionInfo, archived: Boolean) {
        viewModelScope.launch {
            try {
                api.archiveSession(session.id, archived)
                _state.value = _state.value.copy(
                    sessions = _state.value.sessions.filterNot { it.id == session.id },
                    toast = if (archived) "Session archived" else "Session restored",
                )
            } catch (error: Throwable) {
                _state.value = _state.value.copy(toast = error.userMessage())
            }
        }
    }

    fun delete(session: SessionInfo) {
        viewModelScope.launch {
            try {
                api.deleteSession(session.id)
                _state.value = _state.value.copy(
                    sessions = _state.value.sessions.filterNot { it.id == session.id },
                    toast = "Session deleted",
                )
            } catch (error: Throwable) {
                _state.value = _state.value.copy(toast = error.userMessage())
            }
        }
    }

    fun consumeToast() {
        if (_state.value.toast != null) _state.value = _state.value.copy(toast = null)
    }

    fun dismissError() {
        if (_state.value.error != null) _state.value = _state.value.copy(error = null)
    }

    private fun handleEvent(event: ServerEvent) {
        when (event.type) {
            "session.status" -> {
                val sessionID = event.sessionID ?: return
                val status = event.obj("status")?.let {
                    runCatching {
                        com.tom7.openremote.data.AppJson.decodeFromString<SessionStatus>(it.toString())
                    }.getOrNull()
                }
                if (status?.busy == true) {
                    _state.value = _state.value.copy(busy = _state.value.busy + sessionID)
                } else {
                    _state.value = _state.value.copy(busy = _state.value.busy - sessionID)
                }
            }

            "session.execution.started" -> {
                val id = event.sessionID ?: return
                _state.value = _state.value.copy(busy = _state.value.busy + id)
            }

            "session.execution.succeeded", "session.execution.failed", "session.execution.interrupted",
            "session.idle" -> {
                val id = event.sessionID ?: return
                _state.value = _state.value.copy(busy = _state.value.busy - id)
                scheduleRefresh()
            }

            "session.created", "session.renamed", "session.archived", "session.unarchived",
            "session.deleted", "session.moved", "session.model.selected", "session.agent.selected",
            "session.metadata.updated" -> scheduleRefresh()
        }
    }

    private fun scheduleRefresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            delay(250)
            refresh()
        }
    }
}
