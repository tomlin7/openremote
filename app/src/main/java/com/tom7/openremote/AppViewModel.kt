package com.tom7.openremote

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tom7.openremote.data.ApiException
import com.tom7.openremote.data.AppJson
import com.tom7.openremote.data.ConnectionStore
import com.tom7.openremote.data.EventStream
import com.tom7.openremote.data.LocationInfo
import com.tom7.openremote.data.OpenCodeApi
import com.tom7.openremote.data.RealtimeSignal
import com.tom7.openremote.data.SavedConnection
import com.tom7.openremote.data.ServerEvent
import com.tom7.openremote.data.ServerInfo
import com.tom7.openremote.data.ThemeMode
import com.tom7.openremote.data.normalizeServerUrl
import com.tom7.openremote.data.parsePairingInput
import com.tom7.openremote.data.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.contentOrNull

data class ConnectState(
    val busy: Boolean = false,
    val pairing: Boolean = false,
    val error: String? = null,
)

enum class RealtimeStatus { Offline, Connecting, Live }

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as OpenRemoteApp).container
    private val store: ConnectionStore = container.store

    private val _connection = MutableStateFlow(store.loadConnection())
    val connection: StateFlow<SavedConnection?> = _connection.asStateFlow()

    private val _serverInfo = MutableStateFlow<ServerInfo?>(null)
    val serverInfo: StateFlow<ServerInfo?> = _serverInfo.asStateFlow()

    private val _location = MutableStateFlow<LocationInfo?>(null)
    val location: StateFlow<LocationInfo?> = _location.asStateFlow()

    private val _connectState = MutableStateFlow(ConnectState())
    val connectState: StateFlow<ConnectState> = _connectState.asStateFlow()

    private val _realtime = MutableStateFlow(RealtimeStatus.Offline)
    val realtime: StateFlow<RealtimeStatus> = _realtime.asStateFlow()

    private val _themeMode = MutableStateFlow(store.themeMode)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _defaultDelivery = MutableStateFlow(store.defaultDelivery)
    val defaultDelivery: StateFlow<String> = _defaultDelivery.asStateFlow()

    private val _showReasoning = MutableStateFlow(store.showReasoning)
    val showReasoning: StateFlow<Boolean> = _showReasoning.asStateFlow()

    private val _events = MutableSharedFlow<ServerEvent>(
        extraBufferCapacity = 512,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<ServerEvent> = _events.asSharedFlow()

    val lastUrl: String get() = store.lastUrl().orEmpty()

    private var realtimeJob: Job? = null

    var api: OpenCodeApi? = null
        private set

    val apiKey: String
        get() = api?.let { it.baseUrl + "#" + it.authorization.hashCode() }.orEmpty()

    init {
        val saved = _connection.value
        if (saved != null) {
            api = container.api(saved)
            startRealtime()
            viewModelScope.launch {
                runCatching { api?.info() }.onSuccess { _serverInfo.value = it }
                runCatching { api?.location() }.onSuccess { _location.value = it }
            }
        }
        
        // Listen for session completion events and show notifications
        viewModelScope.launch {
            try {
                events.collect { event ->
                    handleSessionEvent(event)
                }
            } catch (e: Exception) {
                android.util.Log.e("AppViewModel", "Error collecting events", e)
            }
        }
    }
    
    private fun handleSessionEvent(event: ServerEvent) {
        val sessionID = event.sessionID ?: return
        
        // Check for completion events
        val outcome = when (event.type) {
            "session.execution.succeeded" -> "succeeded"
            "session.execution.failed" -> "failed"
            "session.execution.interrupted" -> "interrupted"
            "session.idle" -> {
                // Only notify on idle if we know the session was active
                // This prevents duplicate notifications
                return
            }
            else -> return
        }
        
        // Get session title - we'll need to fetch it or use a fallback
        viewModelScope.launch {
            try {
                val session = api?.session(sessionID)
                val title = session?.displayTitle ?: "Session"
                val context = getApplication<OpenRemoteApp>()
                OpenRemoteNotificationManager.notifySessionCompleted(context, title, outcome)
            } catch (e: Exception) {
                // Fallback to session ID if we can't fetch the title
                val context = getApplication<OpenRemoteApp>()
                OpenRemoteNotificationManager.notifySessionCompleted(context, "Session", outcome)
            }
        }
    }

    fun connect(inputUrl: String, password: String) {
        if (_connectState.value.busy) return
        val url = normalizeServerUrl(inputUrl)
        if (url == null) {
            _connectState.value = ConnectState(error = "Enter a valid http:// or https:// server address.")
            return
        }
        viewModelScope.launch {
            _connectState.value = ConnectState(busy = true)
            try {
                establish(url, password)
                _connectState.value = ConnectState()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                _connectState.value = ConnectState(error = error.userMessage())
            }
        }
    }

    fun pair(input: String) {
        if (_connectState.value.busy) return
        val link = parsePairingInput(input, fallbackUrl = store.lastUrl())
        if (link == null) {
            _connectState.value = ConnectState(
                error = "Paste a pairing link or QR payload from `opencode pair`.",
            )
            return
        }
        viewModelScope.launch {
            _connectState.value = ConnectState(busy = true, pairing = true)
            var failure: Throwable? = null
            var failedAt: String? = null
            var expired = false
            var token: String? = null
            var usedUrl: String? = null

            // Try to redeem the code against any of the server's addresses
            for (url in link.urls) {
                try {
                    token = redeem(url, link.code)
                    usedUrl = url
                    break
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    failure = error
                    failedAt = url
                    // Only 401 means the server itself forgot the code. A 403, a 5xx or an HTML
                    // page comes from something in between (tunnel, carrier or Wi-Fi portal) and
                    // must not be reported as an expired code.
                    if (error is ApiException && error.statusCode == 401) {
                        expired = true
                        break
                    }
                }
            }

            // If redeem succeeded, try to establish the connection
            if (token != null && usedUrl != null) {
                try {
                    establish(usedUrl, token)
                    _connectState.value = ConnectState()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    // The token was issued but the connection failed; the code is already consumed.
                    _connectState.value = ConnectState(
                        error = "Paired with $usedUrl, but then could not connect: ${describe(error)}. The code has been used, so pair again for a new one.",
                    )
                }
                return@launch
            }

            val message = when {
                expired -> "The server at $failedAt no longer accepts this code. Codes last five minutes and work once; pair again for a fresh one."
                failure is ApiException -> "The server at $failedAt answered HTTP ${failure.statusCode}: ${failure.body?.trim()?.take(150)?.ifBlank { "no response body" }}"
                failure != null -> "Could not reach $failedAt. ${failure.userMessage()}"
                else -> "Could not reach the server."
            }
            _connectState.value = ConnectState(error = message)
        }
    }

    private fun describe(error: Throwable): String = when (error) {
        is ApiException -> "HTTP ${error.statusCode}: ${error.body?.trim()?.take(150)?.ifBlank { "no response body" }}"
        else -> error.userMessage()
    }

    fun clearPairingError() {
        if (_connectState.value.error != null) _connectState.value = _connectState.value.copy(error = null)
    }

    private suspend fun establish(url: String, password: String) {
        val candidate = container.api(SavedConnection(url, password))
        val info = candidate.info()
        candidate.sessions(limit = 1) // proves the credentials are valid before saving
        val location = runCatching { candidate.location() }.getOrNull()

        container.store.saveConnection(SavedConnection(url, password))
        api = candidate
        _serverInfo.value = info
        _location.value = location
        _connection.value = SavedConnection(url, password)
        startRealtime()
    }

    private suspend fun redeem(baseUrl: String, code: String): String {
        val url = normalizeServerUrl(baseUrl) ?: throw IllegalArgumentException("Invalid pairing address.")
        val request = Request.Builder()
            .url("$url/auth/connect/${URLEncoder.encode(code, "UTF-8")}")
            .header("Accept", "application/json")
            .build()
        val client = container.client.newBuilder().callTimeout(8, TimeUnit.SECONDS).build()
        val response = withContext(Dispatchers.IO) { client.newCall(request).execute() }
        return response.use {
            val body = it.body?.string().orEmpty()
            if (!it.isSuccessful) throw ApiException(it.code, body, body)
            val token = runCatching {
                AppJson.parseToJsonElement(body).jsonObject["token"]?.jsonPrimitive?.contentOrNull
            }.getOrNull()
            token?.takeIf { value -> value.isNotBlank() }
                ?: throw IllegalStateException("The server did not return a pairing token.")
        }
    }

    fun disconnect() {
        container.store.clearConnection()
        realtimeJob?.cancel()
        realtimeJob = null
        api = null
        _connection.value = null
        _serverInfo.value = null
        _location.value = null
        _realtime.value = RealtimeStatus.Offline
        _connectState.value = ConnectState()
    }

    fun retry() {
        val saved = _connection.value ?: return
        connect(saved.url, saved.password)
    }

    fun setTheme(mode: ThemeMode) {
        _themeMode.value = mode
        store.themeMode = mode
    }

    fun setDefaultDelivery(delivery: String) {
        _defaultDelivery.value = if (delivery == "queue") "queue" else "steer"
        store.defaultDelivery = _defaultDelivery.value
    }

    fun setShowReasoning(show: Boolean) {
        _showReasoning.value = show
        store.showReasoning = show
    }

    fun updateServerInfo(info: ServerInfo) {
        _serverInfo.value = info
    }

    private fun startRealtime() {
        val client = api ?: return
        realtimeJob?.cancel()
        _realtime.value = RealtimeStatus.Connecting
        realtimeJob = viewModelScope.launch {
            EventStream(client).stream().collect { signal ->
                when (signal) {
                    is RealtimeSignal.Connected -> _realtime.value = RealtimeStatus.Live
                    is RealtimeSignal.Failed -> {
                        _realtime.value = RealtimeStatus.Connecting
                    }
                    is RealtimeSignal.Event -> {
                        _realtime.value = RealtimeStatus.Live
                        _events.emit(signal.event)
                    }
                }
            }
        }
    }
}
