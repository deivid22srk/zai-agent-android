package com.zai.agent.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * REST-style calls used by the chat-list / drawer UI.
 *
 * Streaming the assistant reply is NOT done here — because chat.z.ai requires
 * an Aliyun captcha widget (JavaScript) before it will accept a completion
 * request, the chat screen uses a WebView to load the web UI directly. This
 * repository only handles endpoints that work with plain Bearer auth.
 */
class ZaiRepository(
    private val httpClient: OkHttpClient,
    private val sessionStore: SessionStore,
    private val json: Json = AppJson,
) {

    /**
     * Calls GET /api/v1/auths/ to validate the session and fetch the user
     * profile. Returns null if the session is invalid (401 / network error).
     */
    fun validateSession(): Flow<Result<ZaiUser>> = flow {
        val request = baseRequest("${SessionStore.ZAI_BASE_URL}/api/v1/auths/").get().build()
        val result = execute(request) { body ->
            json.decodeFromString(ZaiUser.serializer(), body)
        }
        result.onSuccess { user ->
            sessionStore.saveUser(user)
        }
        emit(result)
    }.flowOn(Dispatchers.IO)

    /**
     * GET /api/v1/chats/?page=N — returns the list of conversations.
     * The API returns a plain JSON array (no pagination wrapper).
     */
    fun listConversations(page: Int = 1): Flow<Result<List<ZaiConversation>>> = flow {
        val request = baseRequest("${SessionStore.ZAI_BASE_URL}/api/v1/chats/?page=$page").get().build()
        val result = execute(request) { body ->
            // The server returns a bare JSON array, not an object.
            json.decodeFromString(ListSerializer(ZaiConversation.serializer()), body)
        }
        emit(result)
    }.flowOn(Dispatchers.IO)

    fun createConversation(title: String = "", model: String = "glm-5.2"): Flow<Result<ZaiCreateConversationResponse>> = flow {
        val req = ZaiCreateConversationRequest(
            chat = ZaiCreateConversationRequest.ChatBody(title = title, models = listOf(model))
        )
        val payload = json.encodeToString(ZaiCreateConversationRequest.serializer(), req)
            .toRequestBody(JSON_MEDIA_TYPE)
        val request = baseRequest("${SessionStore.ZAI_BASE_URL}/api/v1/chats/new")
            .post(payload)
            .build()
        val result = execute(request) { body ->
            json.decodeFromString(ZaiCreateConversationResponse.serializer(), body)
        }
        emit(result)
    }.flowOn(Dispatchers.IO)

    fun deleteConversation(id: String): Flow<Result<Unit>> = flow {
        val request = baseRequest("${SessionStore.ZAI_BASE_URL}/api/v1/chats/$id")
            .delete()
            .build()
        val result = execute(request) { _ -> Unit }
        emit(result)
    }.flowOn(Dispatchers.IO)

    fun renameConversation(id: String, title: String): Flow<Result<Unit>> = flow {
        val payload = json.encodeToString(
            JsonObject.serializer(),
            buildJsonObject {
                put("chat", buildJsonObject {
                    put("title", JsonPrimitive(title))
                })
            }
        ).toRequestBody(JSON_MEDIA_TYPE)
        val request = baseRequest("${SessionStore.ZAI_BASE_URL}/api/v1/chats/$id")
            .put(payload)
            .build()
        val result = execute(request) { _ -> Unit }
        emit(result)
    }.flowOn(Dispatchers.IO)

    fun getConversation(id: String): Flow<Result<ZaiChatDetail>> = flow {
        val request = baseRequest("${SessionStore.ZAI_BASE_URL}/api/v1/chats/$id").get().build()
        val result = execute(request) { body ->
            json.decodeFromString(ZaiChatDetail.serializer(), body)
        }
        emit(result)
    }.flowOn(Dispatchers.IO)

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun baseRequest(url: String) = Request.Builder()
        .url(url)
        .header("Authorization", sessionStore.buildAuthHeader() ?: "")
        .header("Accept", "application/json")
        .header("Accept-Language", "pt-BR,pt;q=0.9,en;q=0.8")
        .header("X-FE-Version", SessionStore.ZAI_FE_VERSION)
        .header("Origin", SessionStore.ZAI_ORIGIN)
        .header("Referer", SessionStore.ZAI_ORIGIN + "/")
        .header("User-Agent", SessionStore.ZAI_USER_AGENT)
        .apply {
            sessionStore.buildCookieHeader()?.let { header("Cookie", it) }
        }

    private inline fun <T> execute(
        request: Request,
        parser: (String) -> T
    ): Result<T> = runCatching {
        httpClient.newCall(request).execute().use { r ->
            if (!r.isSuccessful) {
                val body = r.body?.string().orEmpty()
                throw ApiException(r.code, "HTTP ${r.code}: $body")
            }
            val body = r.body?.string().orEmpty()
            parser(body)
        }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
