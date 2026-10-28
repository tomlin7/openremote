package com.tom7.openremote.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed interface RealtimeSignal {
    data object Connected : RealtimeSignal
    data class Failed(val message: String) : RealtimeSignal
    data class Event(val event: ServerEvent) : RealtimeSignal
}

class EventStream(private val api: OpenCodeApi) {
    // The event stream stays open while the session is idle; a read timeout would reconnect for no reason.
    private val client = api.client.newBuilder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    fun stream(): Flow<RealtimeSignal> = flow {
        var backoff = 1_000L
        while (currentCoroutineContext().isActive) {
            var connected = false
            try {
                val request = Request.Builder()
                    .url(api.url("/api/event"))
                    .header("Authorization", api.authorization)
                    .header("Accept", "text/event-stream")
                    .header("Cache-Control", "no-cache")
                    .build()
                val call = client.newCall(request)
                val cancelOnEnd = currentCoroutineContext().job.invokeOnCompletion { call.cancel() }
                try {
                    val response = call.await()
                    response.use {
                        if (!it.isSuccessful) {
                            val body = it.body?.string().orEmpty()
                            throw ApiException(it.code, apiErrorSummary(it.code, body), body)
                        }
                        connected = true
                        backoff = 1_000
                        emit(RealtimeSignal.Connected)
                        val source = it.body?.source() ?: throw IOException("Empty event stream")
                        val data = StringBuilder()
                        while (currentCoroutineContext().isActive) {
                            val line = source.readUtf8Line() ?: break
                            when {
                                line.isEmpty() -> {
                                    if (data.isNotEmpty()) {
                                        parseEvent(data.toString())?.let { event -> emit(RealtimeSignal.Event(event)) }
                                        data.clear()
                                    }
                                }

                                line.startsWith("data:") -> {
                                    if (data.isNotEmpty()) data.append('\n')
                                    data.append(line.removePrefix("data:").trimStart())
                                }

                                line.startsWith(":") -> Unit // heartbeat / comment
                            }
                        }
                    }
                } finally {
                    cancelOnEnd.dispose()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                emit(RealtimeSignal.Failed(error.userMessage()))
            }

            if (connected) {
                delay(400)
            } else {
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(15_000)
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun parseEvent(data: String): ServerEvent? =
        runCatching { AppJson.decodeFromString<ServerEvent>(data) }.getOrNull()

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (continuation.isActive) continuation.resume(response) else response.close()
            }
        })
    }
}
