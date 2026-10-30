package com.tom7.openremote.ui.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tom7.openremote.data.OpenCodeApi
import com.tom7.openremote.data.SessionInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class InboxUiState(
    val activeSessions: List<SessionWithUpdate> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

data class SessionWithUpdate(
    val session: SessionInfo,
    val latestUpdate: String?,
)

class InboxViewModel(
    private val api: OpenCodeApi,
) : ViewModel() {

    private val _state = MutableStateFlow(InboxUiState())
    val state: StateFlow<InboxUiState> = _state.asStateFlow()

    init {
        loadActiveSessions()
    }

    fun refresh() {
        loadActiveSessions()
    }

    private fun loadActiveSessions() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            try {
                // Get all sessions and active session IDs
                val sessionsPage = api.sessions()
                val activeIds = api.activeSessions()

                // Filter to only active sessions
                val activeSessions = sessionsPage.data.filter { it.id in activeIds }

                // Get latest update for each active session
                val sessionsWithUpdates = activeSessions.map { session ->
                    val latestUpdate = getLatestUpdate(session.id)
                    SessionWithUpdate(session, latestUpdate)
                }

                _state.update {
                    it.copy(
                        activeSessions = sessionsWithUpdates,
                        isLoading = false,
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load sessions",
                    )
                }
            }
        }
    }

    private suspend fun getLatestUpdate(sessionId: String): String? {
        return try {
            val messagesPage = api.messages(sessionId, limit = 5, order = "desc")
            val lastAssistantMessage = messagesPage.data.firstOrNull { it.isAssistant && !it.text.isNullOrEmpty() }
            
            lastAssistantMessage?.text?.take(200)
        } catch (e: Exception) {
            null
        }
    }
}
