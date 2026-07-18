package com.zai.agent.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources

/**
 * Low-level client that talks directly to chat.z.ai over HTTPS using the session
 * cookies captured by the WebView login flow.
 *
 * The endpoints mirror the ones used by the web UI at https://chat.z.ai.
 * If chat.z.ai ever ships a breaking API change, only this file needs to be updated.
 */
class ZaiApiClient(
    private val httpClient: OkHttpClient,
    private val json: Json = AppJson,
) {

    /**
     * Streams the assistant reply as a flow of incremental text chunks. The flow
     * completes when the server closes the stream or emits the [DONE] sentinel.
     */
    fun streamMessage(request: ZaiSendMessageRequest): Flow<SseEvent> = callbackFlow {
        val body: RequestBody = json.encodeToString(ZaiSendMessageRequest.serializer(), request)
            .toRequestBody(JSON_MEDIA_TYPE)

        val httpRequest = Request.Builder()
            .url("${SessionStore.ZAI_BASE_URL}$PATH_SEND")
            .post(body)
            .build()

        val factory = EventSources.createFactory(httpClient)
        val source = factory.newEventSource(httpRequest, object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                if (data.isBlank()) return
                if (data == DONE || data == "[DONE]") {
                    trySend(SseEvent.Done)
                    eventSource.cancel()
                    return
                }
                runCatching { json.parseToJsonElement(data).jsonObject }
                    .onSuccess { obj -> trySend(parseEvent(obj)) }
                    .onFailure { trySend(SseEvent.Text(data)) }
            }

            override fun onClosed(eventSource: EventSource) {
                trySend(SseEvent.Done)
                channel.close()
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                val code = response?.code ?: -1
                val msg = response?.message ?: t?.message ?: "unknown error"
                channel.close(ApiException(code, "SSE failure: $msg", t))
            }
        })

        awaitClose { source.cancel() }
    }

    private fun parseEvent(obj: JsonObject): SseEvent {
        val type = obj["type"]?.jsonPrimitive?.contentOrNull
            ?: obj["event"]?.jsonPrimitive?.contentOrNull
            ?: "message"
        val content = obj["content"]?.jsonPrimitive?.contentOrNull
            ?: obj["delta"]?.jsonPrimitive?.contentOrNull
            ?: obj["text"]?.jsonPrimitive?.contentOrNull
            ?: ""
        val conversationId = obj["conversation_id"]?.jsonPrimitive?.contentOrNull
            ?: obj["id"]?.jsonPrimitive?.contentOrNull
        val messageId = obj["message_id"]?.jsonPrimitive?.contentOrNull
        return SseEvent.Delta(type = type, content = content, conversationId = conversationId, messageId = messageId)
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private const val DONE = "done"
        private const val PATH_SEND = "/api/chat/send"
    }
}

sealed interface SseEvent {
    data class Delta(
        val type: String,
        val content: String,
        val conversationId: String? = null,
        val messageId: String? = null,
    ) : SseEvent
    data object Done : SseEvent
    data class Text(val raw: String) : SseEvent
}

class ApiException(val code: Int, message: String, cause: Throwable? = null) : RuntimeException(message, cause)
