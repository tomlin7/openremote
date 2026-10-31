package com.tom7.openremote.ui.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tom7.openremote.data.ModelInfo
import com.tom7.openremote.data.ModelRef
import com.tom7.openremote.data.OpenCodeApi
import com.tom7.openremote.data.ProjectCurrent
import com.tom7.openremote.data.ProjectInfo
import com.tom7.openremote.data.ProviderInfo
import com.tom7.openremote.data.SessionInfo
import com.tom7.openremote.data.userMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.URLEncoder

data class InboxUiState(
    val loading: Boolean = true,
    val projects: List<ProjectInfo> = emptyList(),
    val defaultDirectory: String = "",
    val project: ProjectCurrent? = null,
    val models: List<ModelInfo> = emptyList(),
    val providers: List<ProviderInfo> = emptyList(),
    val selectedModel: ModelRef? = null,
    val branch: String? = null,
    val sending: Boolean = false,
    val error: String? = null,
) {
    val directory: String
        get() = defaultDirectory.ifBlank { projects.firstOrNull()?.canonical.orEmpty() }
}

class InboxViewModel(
    private val api: OpenCodeApi,
) : ViewModel() {

    private val _state = MutableStateFlow(InboxUiState())
    val state: StateFlow<InboxUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)

            val projects = runCatching { api.projects() }.getOrDefault(emptyList())
            val location = runCatching { api.location() }.getOrNull()
            val models = runCatching { api.models() }.getOrDefault(emptyList())
                .filter { it.enabled && it.status != "deprecated" }
            val providers = runCatching { api.providers() }.getOrDefault(emptyList())

            val directory = location?.project?.canonical?.ifBlank { location.directory }.orEmpty()
                .ifBlank { projects.firstOrNull()?.canonical.orEmpty() }
            val selected = _state.value.selectedModel ?: models.firstOrNull()?.ref()
            val branch = readBranch(directory)

            _state.value = _state.value.copy(
                loading = false,
                projects = projects,
                defaultDirectory = directory,
                project = location?.project,
                models = models,
                providers = providers,
                selectedModel = selected,
                branch = branch,
            )
        }
    }

    fun selectModel(model: ModelRef) {
        _state.value = _state.value.copy(selectedModel = model)
    }

    fun send(text: String, delivery: String?, onCreated: (SessionInfo) -> Unit) {
        if (_state.value.sending) return
        val directory = _state.value.directory
        if (directory.isBlank()) {
            _state.value = _state.value.copy(error = "No project available on the server.")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(sending = true, error = null)
            try {
                val session = api.createSession(
                    directory = directory,
                    title = null,
                    agent = null,
                    model = _state.value.selectedModel,
                )
                runCatching { api.sendPrompt(session.id, text, delivery = delivery) }
                _state.value = _state.value.copy(sending = false)
                onCreated(session)
            } catch (error: Throwable) {
                _state.value = _state.value.copy(sending = false, error = error.userMessage())
            }
        }
    }

    /** Best-effort read of the project's current git branch; the API does not expose it directly. */
    private suspend fun readBranch(directory: String): String? {
        if (directory.isBlank()) return null
        val path = URLEncoder.encode("$directory/.git/HEAD", "UTF-8").replace("+", "%20")
        return runCatching {
            val head = String(api.readFile(path)).trim()
            val prefix = "ref: refs/heads/"
            if (head.startsWith(prefix)) head.removePrefix(prefix).ifBlank { null } else null
        }.getOrNull()
    }
}
